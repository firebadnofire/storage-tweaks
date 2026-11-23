#!/usr/bin/env bash
set -euo pipefail
: "${CI_KEY_B64:?Signing key is required}"
: "${CI_KEY_PASSPHRASE:?Signing password is required}"
python3 scripts/ci/artifact.py verify ci-dist
export GNUPGHOME
GNUPGHOME="$(mktemp -d)"
chmod 700 "$GNUPGHOME"
trap 'gpgconf --kill gpg-agent; rm -rf -- "$GNUPGHOME"' EXIT
printf '%s' "$CI_KEY_B64" | base64 --decode | gpg --batch --import
unset CI_KEY_B64
mapfile -t keys < <(gpg --batch --with-colons --list-secret-keys | awk -F: '$1=="sec" {want=1;next} want && $1=="fpr" {print $10;want=0}')
if ((${#keys[@]} != 1)); then echo 'Expected exactly one primary signing key; refusing ambiguous import' >&2; exit 1; fi
gpg --batch --armor --export "${keys[0]}" > ci-dist/signing-key.asc
cd ci-dist
sha256sum --check --strict SHA256SUMS
for file in firebadnofire-storage-tweaks-*.jar SHA256SUMS build.json; do
  printf '%s\n' "$CI_KEY_PASSPHRASE" | gpg --batch --yes --local-user "${keys[0]}" --pinentry-mode loopback --passphrase-fd 0 --armor --detach-sign -- "$file"
  gpg --batch --verify "$file.asc" "$file"
done
unset CI_KEY_PASSPHRASE
echo "Signing fingerprint: ${keys[0]}"
