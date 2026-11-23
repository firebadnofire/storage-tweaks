"""Launch built Forge 1.12.2 fixtures under linux-virtual-display.sh (stdlib only)."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile


def linux_path(value):
    if len(value) > 2 and value[1] == ':':
        return Path('/mnt') / value[0].lower() / value[3:].replace('\\', '/')
    return Path(value)


parser = argparse.ArgumentParser()
parser.add_argument('--manifest', required=True, type=Path)
parser.add_argument('--instance', required=True, type=Path)
parser.add_argument('--native-cache', required=True, type=Path)
parser.add_argument('--mode', choices=['integrated', 'contention', 'vanilla', 'optional-server', 'pack', 'row-swap', 'rapid-punch', 'rapid-punch-creative', 'tool-override', 'tool-override-creative', 'chest-rows', 'chest-rows-creative'], default='integrated')
parser.add_argument('--name', default='StorageTest')
parser.add_argument('--heap-gb', type=int, choices=[2, 6], help='Explicit isolated client heap; default 6 for pack, otherwise 2')
args = parser.parse_args()
if not os.environ.get('DISPLAY') or os.environ['DISPLAY'] == ':0':
    parser.error('Run through linux-virtual-display.sh on a private display')
if not args.name.replace('_', '').isalnum() or not 1 <= len(args.name) <= 16:
    parser.error('Invalid test username')
manifest = json.loads(args.manifest.read_text())
args.native_cache.mkdir(parents=True, exist_ok=True)
# Official 1.12.2 version.json Linux classifiers; never use Windows natives.
natives = [
    ('org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209-natives-linux.jar', '931074f46c795d2f7b30ed6395df5715cfd7675b'),
    ('net/java/jinput/jinput-platform/2.0.5/jinput-platform-2.0.5-natives-linux.jar', '7ff832a6eb9ab6a767f1ade2b548092d0fa64795'),
    ('com/mojang/text2speech/1.10.3/text2speech-1.10.3-natives-linux.jar', 'ab7896aec3b3dd272b06194357f2d98f832c0cfc'),
]
for artifact, checksum in natives:
    archive = args.native_cache / Path(artifact).name
    if not archive.exists():
        with urllib.request.urlopen('https://libraries.minecraft.net/' + artifact, timeout=60) as response:
            data = response.read()
        if hashlib.sha1(data).hexdigest() != checksum:
            raise RuntimeError('Downloaded native checksum mismatch: ' + artifact)
        temporary = archive.with_name(archive.name + '.' + str(os.getpid()) + '.tmp')
        temporary.write_bytes(data)
        temporary.replace(archive)
    if hashlib.sha1(archive.read_bytes()).hexdigest() != checksum:
        raise RuntimeError('Cached native checksum mismatch: ' + artifact)
    with zipfile.ZipFile(archive) as jar:
        for entry in jar.infolist():
            if entry.filename.endswith('.so'):
                destination = args.native_cache / Path(entry.filename).name
                data = jar.read(entry)
                if not destination.exists() or destination.read_bytes() != data:
                    temporary = destination.with_name(destination.name + '.' + str(os.getpid()) + '.tmp')
                    temporary.write_bytes(data)
                    temporary.replace(destination)
args.instance.mkdir(parents=True, exist_ok=True)
(args.instance / 'mods').mkdir(exist_ok=True)
(args.instance / 'tmp').mkdir(exist_ok=True)
if args.mode == 'optional-server' and any((args.instance / 'mods').iterdir()):
    expected = linux_path(manifest['baseline']).name
    if any(file.name != expected for file in (args.instance / 'mods').iterdir()):
        raise RuntimeError('Optional-server fixture requires an instance containing only the baseline test mod')
for key in (['baseline'] if args.mode == 'optional-server' else ['mod', 'fixture']):
    source = linux_path(manifest[key])
    shutil.copy2(source, args.instance / 'mods' / source.name)
options = args.instance / 'options.txt'
lines = options.read_text().splitlines() if options.exists() else []
lines = [line for line in lines if not line.startswith(('soundCategory_master:', 'limitFramerate:', 'pauseOnLostFocus:'))]
options.write_text('\n'.join(lines + ['soundCategory_master:0.0', 'limitFramerate:30', 'pauseOnLostFocus:false']) + '\n')
command = [str(Path(os.environ['JAVA_HOME']) / 'bin/java'), '-Xmx%dG' % (args.heap_gb or (6 if args.mode == 'pack' else 2)),
           '-Djava.library.path=' + str(args.native_cache),
           '-Djava.io.tmpdir=' + str(args.instance / 'tmp'),
           '-DstorageTweaks.runtimeTest=true', '-DstorageTweaks.runtimeMode=' + args.mode,
           '-cp', ':'.join(str(linux_path(path)) for path in manifest['classpath']),
           'net.minecraft.launchwrapper.Launch', '--username', args.name,
           '--version', '1.12.2', '--gameDir', str(args.instance),
           '--assetsDir', str(linux_path(manifest['assets'])), '--assetIndex', '1.12',
           '--accessToken', '0', '--userProperties', '{}',
           '--tweakClass', 'net.minecraftforge.fml.common.launcher.FMLTweaker']
print('PRIVATE_GUI_TEST', args.mode, args.name, os.environ['DISPLAY'], 'master=0 software-rendering', flush=True)
raise SystemExit(subprocess.call(command, cwd=args.instance))
