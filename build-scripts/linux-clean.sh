#!/usr/bin/env bash
# Removes Gradle outputs and distribution JARs created by linux-build.sh.
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd -- "$script_dir/.." && pwd -P)"
gradle_wrapper="$repo_root/gradlew"
dist_dir="$repo_root/dist"

fail() {
    printf 'Clean failed: %s\n' "$1" >&2
    exit 1
}

command -v java >/dev/null 2>&1 || fail 'Java is required to run the Gradle clean task. Install a local compatible JDK and put it on PATH.'
[[ -f "$gradle_wrapper" ]] || fail "Gradle wrapper not found at $gradle_wrapper. Restore the repository wrapper files."

(
    cd -- "$repo_root"
    if [[ -x "$gradle_wrapper" ]]; then
        "$gradle_wrapper" clean
    else
        bash "$gradle_wrapper" clean
    fi
)

if [[ -d "$dist_dir" ]]; then
    find "$dist_dir" -maxdepth 1 -type f -name 'firebadnofire-storage-tweaks-*-mc*-forge.jar' -delete
    find "$dist_dir" -maxdepth 1 -type d -name '.staging.*' -exec rm -rf -- {} +
fi

printf 'Removed Gradle build outputs and Storage Tweaks distribution artifacts.\n'
