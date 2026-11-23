#!/usr/bin/env bash
set -euo pipefail
export GNUPGHOME
GNUPGHOME="$(mktemp -d)"
chmod 700 "$GNUPGHOME"
trap 'gpgconf --kill gpg-agent; rm -rf -- "$GNUPGHOME"' EXIT
gpg --batch --import ci-dist/signing-key.asc
python3 scripts/ci/release.py
