#!/usr/bin/env bash
# Adapted from ElytraPlus/WorldSafe: publish the verified build without rebuilding it.
set -euo pipefail

version="${1:?Usage: create-release.sh VERSION ARTIFACT_DIRECTORY ARTIFACT EXPECTED_SHA256}"
artifact_directory="${2:?Missing artifact directory}"
artifact="${3:?Missing artifact filename}"
expected_sha256="${4:?Missing build SHA-256}"

if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z]+([.-][0-9A-Za-z]+)*)?$ ]] ||
   [[ ! "$artifact" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*\.jar$ ]] ||
   [[ ! "$expected_sha256" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Invalid release metadata." >&2
  exit 1
fi

cd "$artifact_directory"
shasum -a 256 -c "$artifact.sha256"
actual_sha256="$(shasum -a 256 "$artifact")"
if [[ "${actual_sha256%% *}" != "$expected_sha256" ]]; then
  echo "Downloaded JAR differs from the verified build." >&2
  exit 1
fi

# Exit code 2 means no matching tag; network/authentication errors must abort.
set +e
git ls-remote --exit-code --tags origin "refs/tags/v$version" > /dev/null
tag_status=$?
set -e
if [[ "$tag_status" -eq 0 ]]; then
  echo "Tag v$version already exists; refusing to overwrite an existing release." >&2
  exit 1
elif [[ "$tag_status" -ne 2 ]]; then
  echo "Could not check existing tags." >&2
  exit "$tag_status"
fi

release_args=(release create "v$version" "$artifact" "$artifact.sha256"
  --target "${GITHUB_SHA:?Missing release commit SHA}"
  --title "VillagerVerdict v$version" --generate-notes)
if [[ "$version" == *-* ]]; then
  release_args+=(--prerelease --latest=false)
fi
gh "${release_args[@]}"
