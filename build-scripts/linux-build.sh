#!/usr/bin/env bash
# Builds the distributable Forge JAR with the repository's Gradle wrapper.
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd -- "$script_dir/.." && pwd -P)"
gradle_wrapper="$repo_root/gradlew"
dist_dir="$repo_root/dist"
staging_dir=""

fail() {
    printf 'Build failed: %s\n' "$1" >&2
    exit 1
}

cleanup() {
    if [[ -n "$staging_dir" && -d "$staging_dir" ]]; then
        rm -rf -- "$staging_dir"
    fi
}
trap cleanup EXIT

command -v java >/dev/null 2>&1 || fail 'Java is required. Install a local JDK compatible with this Java 8-targeted project and put it on PATH.'
[[ -f "$gradle_wrapper" ]] || fail "Gradle wrapper not found at $gradle_wrapper. Restore the repository wrapper files."

printf 'Building with the checked-in Gradle wrapper...\n'
(
    cd -- "$repo_root"
    if [[ -x "$gradle_wrapper" ]]; then
        "$gradle_wrapper" build
    else
        # A clone may not preserve the executable bit; Bash can still run the checked-in wrapper.
        bash "$gradle_wrapper" build
    fi
)

mapfile -t artifacts < <(find "$repo_root" -type f -path '*/build/libs/firebadnofire-storage-tweaks-*-mc*-forge.jar' -print | sort)
(( ${#artifacts[@]} > 0 )) || fail 'Gradle completed but produced no distributable Forge JAR under */build/libs. Check the Forge module build output.'

mkdir -p -- "$dist_dir"
staging_dir="$(mktemp -d "$dist_dir/.staging.XXXXXX")"

for artifact in "${artifacts[@]}"; do
    artifact_name="$(basename -- "$artifact")"
    [[ -s "$artifact" ]] || fail "Expected artifact is empty: $artifact"
    [[ ! -e "$staging_dir/$artifact_name" ]] || fail "Two modules produced the same distribution filename: $artifact_name"
    cp -- "$artifact" "$staging_dir/$artifact_name"
done

# Only replace prior artifacts owned by this project; unrelated files in dist are preserved.
find "$dist_dir" -maxdepth 1 -type f -name 'firebadnofire-storage-tweaks-*-mc*-forge.jar' -delete
for staged_artifact in "$staging_dir"/*.jar; do
    mv -- "$staged_artifact" "$dist_dir/$(basename -- "$staged_artifact")"
done
rmdir -- "$staging_dir"
staging_dir=""

printf 'Distribution artifact(s):\n'
find "$dist_dir" -maxdepth 1 -type f -name 'firebadnofire-storage-tweaks-*-mc*-forge.jar' -print | sort
