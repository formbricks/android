#!/usr/bin/env bash
# Builds the playground and its golden journey against the E2E server as the emulator sees it: the
# server URL ($E2E_EMULATOR_URL, the TLS proxy) and workspace go in as BuildConfig fields.
# run-journey.sh passes the same properties, so its connectedDebugAndroidTest reuses this build.
set -euo pipefail
# shellcheck source-path=SCRIPTDIR source=lib.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

repo_root="$(cd "${E2E_ANDROID_DIR}/../.." && pwd)"
# A plain assignment so set -e stops on a failure (see run-journey.sh).
workspace_id="$(server_workspace_id)"
gradle_args_lines="$(playground_gradle_args "${E2E_EMULATOR_URL}" "${workspace_id}")"
mapfile -t gradle_args <<<"${gradle_args_lines}"

log "Building the playground for ${E2E_EMULATOR_URL}"
cd "${repo_root}"
./gradlew --console=plain "${gradle_args[@]}" :playground:assembleDebug :playground:assembleDebugAndroidTest
