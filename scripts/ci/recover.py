"""Recover a failed release from the original tagged build artifacts, without rebuilding."""
import io
import os
import pathlib
import re
import zipfile

from artifact import require, verify
from release import API, release_tag


def validate_source_run(api, run_id, tag):
    require(re.fullmatch(r'[1-9][0-9]*', run_id), 'Invalid recovery run ID')
    run = api.repo('GET', '/actions/runs/' + run_id)
    require(run['event'] == 'push' and run['workflow_id'] == 'main.yml' and run['prettyref'] == tag,
            'Recovery source must be the original tag-push release workflow')
    require(re.fullmatch(r'[0-9a-f]{40}', run['commit_sha']), 'Invalid source-run commit')
    jobs = api.repo('GET', '/actions/runs/' + run_id + '/jobs')
    for name in ('build', 'minecraft-smoke'):
        matched = [job for job in jobs if job['name'] == name]
        require(len(matched) == 1 and matched[0]['status'] == 'success',
                'Original {} job did not pass'.format(name))
    return run


def extract(data, destination):
    destination = pathlib.Path(destination)
    require(not destination.exists(), 'Recovery destination already exists: ' + str(destination))
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for member in archive.infolist():
            path = pathlib.PurePosixPath(member.orig_filename)
            require(not path.is_absolute() and '..' not in path.parts and '\\' not in member.orig_filename and
                    ':' not in member.orig_filename, 'Unsafe recovery ZIP member: ' + member.orig_filename)
            require((member.external_attr >> 16) & 0o170000 != 0o120000, 'Recovery ZIP contains a symlink')
        destination.mkdir(parents=True)
        archive.extractall(destination)


def main():
    tag = release_tag()
    api = API(os.environ.get('RELEASE_TOKEN'))
    run = validate_source_run(api, os.environ.get('RECOVERY_RUN_ID', ''), tag)
    artifacts = api.repo('GET', '/actions/runs/{}/artifacts'.format(os.environ['RECOVERY_RUN_ID']))
    for name, directory in (('distributable', 'ci-dist'), ('minecraft-evidence', 'smoke-result')):
        matched = [artifact for artifact in artifacts if artifact['name'] == name and not artifact['expired']]
        require(len(matched) == 1, 'Expected one unexpired original artifact: ' + name)
        extract(api.repo('GET', '/actions/artifacts/{}/zip'.format(matched[0]['id']), raw=True), directory)
    record, _ = verify('ci-dist')
    require(record['commit'] == run['commit_sha'], 'Recovered artifact commit differs from source run')
    print('Recovered original tested artifact: {} {}'.format(tag, record['sha256']))


if __name__ == '__main__':
    main()
