"""Exercise real signing with a disposable password-protected key."""
import base64
import os
import pathlib
import shutil
import subprocess
import tempfile


def main():
    with tempfile.TemporaryDirectory(prefix='storage-tweaks-sign-test-') as temporary:
        root = pathlib.Path(temporary)
        keyhome = root / 'key'
        keyhome.mkdir(mode=0o700)
        env = dict(os.environ, GNUPGHOME=str(keyhome))
        try:
            subprocess.run(['gpg', '--batch', '--pinentry-mode', 'loopback', '--passphrase-fd', '0',
                            '--quick-generate-key', 'Storage Tweaks disposable CI test key', 'rsa2048', 'sign', '1d'],
                           input=b'test-only-password\n', env=env, check=True, capture_output=True)
            exported = subprocess.run(['gpg', '--batch', '--pinentry-mode', 'loopback', '--passphrase-fd', '0',
                                       '--export-secret-keys'], input=b'test-only-password\n', env=env,
                                      check=True, capture_output=True).stdout
            shutil.copytree('ci-dist', root / 'ci-dist')
            shutil.copytree('scripts/ci', root / 'scripts/ci', ignore=shutil.ignore_patterns('__pycache__'))
            signenv = dict(os.environ, CI_KEY_B64=base64.b64encode(exported).decode(),
                           CI_KEY_PASSPHRASE='test-only-password')
            subprocess.run(['bash', 'scripts/ci/sign.sh'], cwd=root, env=signenv, check=True)
            signenv['CI_KEY_PASSPHRASE'] = 'incorrect-password'
            failed = subprocess.run(['bash', 'scripts/ci/sign.sh'], cwd=root, env=signenv,
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            if failed.returncode == 0:
                raise RuntimeError('Incorrect signing password was accepted')
        finally:
            subprocess.run(['gpgconf', '--kill', 'gpg-agent'], env=env, check=True)


if __name__ == '__main__':
    main()
