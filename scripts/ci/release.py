"""Publish signed tag artifacts to the configured Forgejo project.

The release is created as a draft, all asset bytes and signatures are verified,
then it is made public. Existing published assets are never replaced.
"""
import json
import os
import pathlib
import re
import subprocess
import urllib.error
import urllib.parse
import urllib.request
import uuid

from artifact import require, verify


API_BASE = 'https://pubcode.archuser.org/api/v1'
REPOSITORY = 'firebadnofire/storage-tweaks'
SOURCE_REMOTES = {
    'ssh://git@pubcode-ssh.archuser.org:2222/firebadnofire/storage-tweaks',
    'https://pubcode.archuser.org/firebadnofire/storage-tweaks',
    'https://pubcode.archuser.org/firebadnofire/storage-tweaks.git',
}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class API:
    def __init__(self, token):
        require(token, 'Missing RELEASE_TOKEN')
        self.token = token

    def request(self, method, path, body=None, missing=False, raw=False, content_type='application/json'):
        url = path if path.startswith('https://') else API_BASE + path
        require(urllib.parse.urlsplit(url).netloc == 'pubcode.archuser.org', 'Untrusted authenticated endpoint')
        if isinstance(body, dict):
            body = json.dumps(body).encode()
        request = urllib.request.Request(url, data=body, method=method, headers={
            'Authorization': 'Bearer ' + self.token,
            'Accept': 'application/octet-stream' if raw else 'application/json',
            'Content-Type': content_type, 'User-Agent': 'StorageTweaks-CI/1.0'})
        try:
            with urllib.request.build_opener(NoRedirect).open(request, timeout=120) as response:
                data = response.read()
        except urllib.error.HTTPError as error:
            if missing and error.code == 404:
                return None
            detail = error.read().decode(errors='replace')[:1500].replace(self.token, '[redacted]')
            raise RuntimeError('{} {}: HTTP {}: {}'.format(method, urllib.parse.urlsplit(url).path, error.code, detail))
        return data if raw else (json.loads(data) if data else None)

    def repo(self, method, path='', body=None, **kwargs):
        return self.request(method, '/repos/' + REPOSITORY + path, body, **kwargs)


def release_tag():
    event, ref = os.environ.get('GITHUB_EVENT_NAME'), os.environ.get('GITHUB_REF')
    if event == 'push':
        require(ref and ref.startswith('refs/tags/'), 'Publication requires a pushed v* or V* tag')
        tag = ref[len('refs/tags/'):]
    else:
        require(event == 'workflow_dispatch', 'Publication requires a tag push or manual recovery dispatch')
        tag = os.environ.get('RELEASE_TAG', '')
    require(re.fullmatch(r'[vV][A-Za-z0-9._+-]*', tag) and '..' not in tag and not tag.endswith('.'),
            'Publication requires a safe v* or V* tag')
    return tag


def notes(record, tag):
    return ('Repository: {}\nCommit: {}\nTag: {}\nVersion: {}\nBuild system: Gradle 4.9\nJava: 8\n'
            'Minecraft: {}\nForge: {}\nLicense: AGPL-3.0-or-later\n\n'
            'Validation: Gradle build/reobfuscation and {} unit tests passed; the exact staged binary passed '
            'a disposable Forge dedicated-server startup, loaded-mod, ten-second health, and graceful shutdown smoke test. '
            'SHA-256 checksums and detached GPG signatures were verified before upload; Forgejo asset names and '
            'reported byte sizes were checked before publication. Remote asset downloads are intentionally not performed.\n\n'
            'Smoke testing does not verify client GUI behavior, finder behavior, punch-deposit gameplay, or modpack compatibility.\n').format(
                REPOSITORY, record['commit'], tag, record['version'], record['minecraft'], record['forge'], record['tests'])


def files(directory):
    return {path.name: path.read_bytes() for path in sorted(pathlib.Path(directory).iterdir()) if path.is_file()}


def check_assets(api, release, expected):
    assets = api.repo('GET', '/releases/{}/assets?per_page=100&limit=100'.format(release['id']))
    inventory = {asset['name']: asset for asset in assets}
    require(len(inventory) == len(assets) and set(inventory) == set(expected), 'Remote asset inventory mismatch')
    for name, asset in inventory.items():
        require(asset.get('type', 'attachment') == 'attachment', 'External release assets are not supported')
        require(asset.get('size') == len(expected[name]), 'Remote asset size differs: ' + name)


def main():
    record, _ = verify('ci-dist')
    tag = release_tag()
    source = subprocess.check_output(['git', 'remote', 'get-url', 'origin'], text=True).strip()
    require(source in SOURCE_REMOTES, 'Unexpected source remote')
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    require(commit == record['commit'], 'Staged artifact was not built from the checked-out commit')
    api = API(os.environ.get('RELEASE_TOKEN'))
    tag_info = api.repo('GET', '/tags/' + urllib.parse.quote(tag, safe=''), missing=True)
    require(tag_info is not None, 'Remote release tag does not exist')
    tagged_commit = tag_info['commit']['sha']
    require(re.fullmatch(r'[0-9a-f]{40}', tagged_commit), 'Remote release tag has an invalid commit')
    if os.environ.get('RECOVERY_RUN_ID'):
        require(os.environ.get('GITHUB_EVENT_NAME') == 'workflow_dispatch',
                'Recovery requires a manual workflow dispatch')
        require(record['commit'] == tagged_commit, 'Recovered artifact does not match the release tag')
        main_commit = subprocess.check_output(['git', 'rev-parse', 'refs/remotes/origin/main'], text=True).strip()
        require(commit == main_commit, 'Recovery checkout is not current main')
    else:
        require(tagged_commit == commit == record['commit'],
                'Release tag, checkout, and staged artifact must match')
    title, body = 'Storage Tweaks ' + tag, notes(record, tag)
    release = api.repo('GET', '/releases/tags/' + urllib.parse.quote(tag, safe=''), missing=True)
    if release is None:
        release = api.repo('POST', '/releases', dict(tag_name=tag, target_commitish=commit, name=title,
                                                      body=body, draft=True, prerelease=False))
    require(release['tag_name'] == tag and release['name'] == title and
            release['body'].replace('\r\n', '\n') == body and not release['prerelease'],
            'Existing release metadata differs; refusing to modify it')
    expected = files('ci-dist')
    current = api.repo('GET', '/releases/{}/assets?per_page=100&limit=100'.format(release['id']))
    inventory = {asset['name']: asset for asset in current}
    require(len(inventory) == len(current) and set(inventory) <= set(expected), 'Unexpected existing assets')
    for name, data in expected.items():
        if name in inventory:
            continue
        require(release['draft'], 'Published release is incomplete; manual recovery required')
        boundary = uuid.uuid4().hex
        body_bytes = ('--{}\r\nContent-Disposition: form-data; name="attachment"; filename="{}"\r\n'
                      'Content-Type: application/octet-stream\r\n\r\n').format(boundary, name).encode() + data + \
                     ('\r\n--{}--\r\n'.format(boundary)).encode()
        api.repo('POST', '/releases/{}/assets'.format(release['id']), body_bytes,
                 content_type='multipart/form-data; boundary=' + boundary)
    check_assets(api, release, expected)
    if release['draft']:
        api.repo('PATCH', '/releases/{}'.format(release['id']), dict(draft=False, prerelease=False))
    completed = api.repo('GET', '/releases/{}'.format(release['id']))
    require(not completed['draft'], 'Release remains a draft')
    check_assets(api, completed, expected)
    print('Published verified Forgejo release: ' + tag)


if __name__ == '__main__':
    main()
