"""Validate and stage only Storage Tweaks' release artifacts."""
import hashlib
import json
import pathlib
import re
import shutil
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile


ROOT = pathlib.Path('.')
MODULE = ROOT / 'forge-1.12.2'
PREFIX = 'firebadnofire-storage-tweaks-'


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def version():
    match = re.search(r"^\s*version = '([^']+)'\s*$", (ROOT / 'build.gradle').read_text(), re.M)
    require(match is not None and re.fullmatch(r'[0-9A-Za-z.+-]+', match.group(1)), 'Unsafe project version')
    build = (MODULE / 'build.gradle').read_text()
    require("minecraft 'net.minecraftforge:forge:1.12.2-14.23.5.2864'" in build,
            'Unexpected Minecraft/Forge baseline')
    return match.group(1)


def names(project_version):
    stem = PREFIX + project_version + '-mc1.12.2-forge'
    return (stem + '.jar', stem + '-sources.jar')


def inspect_binary(path, project_version):
    binary, _ = names(project_version)
    require(path.name == binary and path.is_file() and path.stat().st_size > 0, 'Unexpected binary artifact')
    with zipfile.ZipFile(path) as archive:
        metadata = json.loads(archive.read('mcmod.info'))
        require(len(metadata) == 1 and metadata[0]['modid'] == 'firebadnofire_storage_tweaks', 'Wrong mod ID')
        require(metadata[0]['version'] == project_version and metadata[0]['mcversion'] == '1.12.2',
                'Metadata version or Minecraft mismatch')
        require('LICENSE' in archive.namelist() and 'NOTICE' in archive.namelist(), 'License notices missing')
        entry = archive.read('org/firebadnofire/storagetweaks/StorageTweaks.class')
        require(struct.unpack('>H', entry[6:8])[0] == 52, 'Binary is not Java 8 bytecode')
        require(b'firebadnofire_storage_tweaks' in entry and project_version.encode() in entry,
                'Compiled mod contract missing')
        classes = [name for name in archive.namelist() if name.endswith('.class')]
        require(classes and not any(name.startswith('net/minecraftforge/') for name in classes),
                'Invalid or bundled Forge classes')
    return hashlib.sha256(path.read_bytes()).hexdigest()


def inspect_sources(path, project_version):
    _, sources = names(project_version)
    require(path.name == sources and path.is_file() and path.stat().st_size > 0, 'Unexpected source artifact')
    with zipfile.ZipFile(path) as archive:
        require('LICENSE' in archive.namelist() and 'NOTICE' in archive.namelist(), 'Source notices missing')
        require('org/firebadnofire/storagetweaks/StorageTweaks.java' in archive.namelist(), 'Source entry missing')
    return hashlib.sha256(path.read_bytes()).hexdigest()


def checksums(directory, files):
    return ''.join(hashlib.sha256((directory / name).read_bytes()).hexdigest() + '  ' + name + '\n'
                   for name in files)


def verify(directory):
    directory = pathlib.Path(directory)
    record = json.loads((directory / 'build.json').read_text())
    project_version = record['version']
    binary, sources = names(project_version)
    digests = {binary: inspect_binary(directory / binary, project_version),
               sources: inspect_sources(directory / sources, project_version)}
    require(record['sha256'] == digests[binary] and record['sources_sha256'] == digests[sources],
            'Artifact identity mismatch')
    require(re.fullmatch(r'[0-9a-f]{40}', record['commit']), 'Invalid commit identity')
    require((directory / 'SHA256SUMS').read_text() == checksums(directory, (binary, sources)),
            'Checksum manifest mismatch')
    return record, tuple(directory / name for name in (binary, sources))


def test_count():
    reports = list((ROOT / 'common/build/test-results/test').glob('TEST-*.xml'))
    reports += list((MODULE / 'build/test-results/test').glob('TEST-*.xml'))
    suites = [ET.parse(report).getroot() for report in reports]
    require(suites, 'No unit-test evidence')
    require(all(int(suite.get(key, '0')) == 0 for suite in suites for key in ('failures', 'errors', 'skipped')),
            'Unit tests failed, errored, or skipped')
    total = sum(int(suite.get('tests', '0')) for suite in suites)
    require(total > 0, 'No unit tests ran')
    return total


def stage(directory):
    project_version = version()
    binary, sources = names(project_version)
    source = MODULE / 'build/libs'
    digest = inspect_binary(source / binary, project_version)
    source_digest = inspect_sources(source / sources, project_version)
    directory = pathlib.Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    require(not list(directory.iterdir()), 'Artifact staging directory must be empty')
    shutil.copyfile(source / binary, directory / binary)
    shutil.copyfile(source / sources, directory / sources)
    record = dict(version=project_version, sha256=digest, sources_sha256=source_digest,
                  commit=subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
                  tests=test_count(), minecraft='1.12.2', forge='14.23.5.2864')
    (directory / 'build.json').write_text(json.dumps(record, indent=2) + '\n')
    (directory / 'SHA256SUMS').write_text(checksums(directory, (binary, sources)))
    verify(directory)
    print(json.dumps(record))


if __name__ == '__main__':
    if len(sys.argv) != 3 or sys.argv[1] not in ('stage', 'verify'):
        raise SystemExit('Usage: artifact.py stage|verify <directory>')
    if sys.argv[1] == 'stage':
        stage(sys.argv[2])
    else:
        print(json.dumps(verify(sys.argv[2])[0]))
