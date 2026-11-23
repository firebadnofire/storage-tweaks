"""Launch the staged production JAR on a disposable Forge 1.12.2 server."""
import hashlib
import json
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request

from artifact import require, verify


def main():
    record, artifacts = verify(sys.argv[1])
    jar = artifacts[0]
    output = pathlib.Path(sys.argv[2]).resolve()
    output.mkdir(parents=True, exist_ok=True)
    result = dict(commit=record['commit'], sha256=record['sha256'], version=record['version'],
                  startup=False, mod_loaded=False, observation=False, shutdown=False, passed=False)
    process = None
    temporary = tempfile.TemporaryDirectory(prefix='storage-tweaks-smoke-')
    server = pathlib.Path(temporary.name)
    try:
        java_version = subprocess.run(['java', '-version'], capture_output=True, text=True, check=True)
        require('version "1.8.' in java_version.stderr, 'Smoke test requires Java 8')
        forge = 'forge-1.12.2-14.23.5.2864'
        url = 'https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2864/' + forge + '-installer.jar'
        installer = server / 'installer.jar'
        request = urllib.request.Request(url, headers={'User-Agent': 'StorageTweaks-CI/1.0'})
        with urllib.request.urlopen(request, timeout=120) as response:
            installer.write_bytes(response.read())
        result['installer_sha256'] = hashlib.sha256(installer.read_bytes()).hexdigest()
        with (output / 'installer.log').open('w') as log:
            subprocess.run(['java', '-jar', str(installer), '--installServer'], cwd=server,
                           stdout=log, stderr=subprocess.STDOUT, timeout=600, check=True)
        (server / 'mods').mkdir()
        installed = server / 'mods' / jar.name
        shutil.copyfile(jar, installed)
        require(hashlib.sha256(installed.read_bytes()).hexdigest() == record['sha256'], 'Installed JAR differs')
        (server / 'eula.txt').write_text('eula=true\n')
        (server / 'server.properties').write_text(
            'server-ip=127.0.0.1\nserver-port=25565\nonline-mode=true\nenable-rcon=false\n'
            'enable-query=false\nmax-players=1\nview-distance=2\nlevel-type=FLAT\n'
            'generate-structures=false\nspawn-monsters=false\nspawn-animals=false\n')
        with (output / 'console.log').open('w') as log:
            process = subprocess.Popen(['java', '-Xms256M', '-Xmx1536M', '-jar', forge + '.jar', 'nogui'],
                                       cwd=server, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT,
                                       text=True)
            started = time.monotonic()
            ready = None
            while time.monotonic() - started < 600:
                text = (output / 'console.log').read_text(errors='replace')
                require(process.poll() is None, 'Server terminated unexpectedly before shutdown')
                # Forge 1.12.2's official installer emits this unsigned-coremod
                # warning at ERROR level before it loads any mod. It is expected
                # for the unmodified 14.23.5.2864 server and is not a mod failure.
                checked = text.replace('[main/ERROR] [FML]: FML appears to be missing any signature data. '
                                       'This is not a good thing', '')
                require(not re.search(r'Encountered an unexpected exception|Failed to load|MissingModsException|'
                                     r'LoaderException|\bFATAL\b|\bERROR\b', checked), 'Server logged a startup/runtime error')
                if re.search(r'Done \([\d.,]+s\)!', text):
                    if ready is None:
                        ready = time.monotonic()
                        result['startup'] = True
                        result['ready_after_seconds'] = ready - started
                    result['mod_loaded'] = 'firebadnofire_storage_tweaks' in text
                    require(result['mod_loaded'], 'World ready but Storage Tweaks loading evidence absent')
                    if time.monotonic() - ready >= 10:
                        result['observation'] = True
                        result['healthy_seconds'] = time.monotonic() - ready
                        break
                time.sleep(0.5)
            require(result['observation'], 'Server startup or 10-second observation timed out')
            process.stdin.write('stop\n')
            process.stdin.flush()
            require(process.wait(timeout=60) == 0, 'Server shutdown returned nonzero')
            text = (output / 'console.log').read_text(errors='replace')
            require('Stopping server' in text and 'Saving chunks' in text, 'Graceful save/shutdown evidence missing')
            result['shutdown'] = True
            result['passed'] = True
    except Exception as error:
        result['error'] = str(error)
        raise
    finally:
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        for name in ('logs', 'crash-reports'):
            if (server / name).exists():
                shutil.copytree(server / name, output / name, dirs_exist_ok=True)
        temporary.cleanup()
        (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
        print(json.dumps(result))


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('Usage: smoke.py <artifact-directory> <result-directory>')
    main()
