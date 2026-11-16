#!/usr/bin/env bash
# Runs the Espresso golden journey (playground/src/androidTest) on the running emulator, with one
# retry inside this step: the merge queue drops a PR before anyone could re-run the job. Each
# attempt's test results and logcat are kept in $E2E_REPORT_DIR.
# Env: E2E_USER_ID and E2E_FEEDBACK (the same values assert-response.sh checks).
# Expects: https-proxy.sh up, an emulator (API <= 33, google_apis) and the playground APKs built by
# build-playground.sh (the same server URL and workspace, so Gradle does not rebuild them).
set -euo pipefail
# shellcheck source-path=SCRIPTDIR source=lib.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"
E2E_WORKSPACE_ID="$(server_workspace_id)"

: "${E2E_USER_ID:?set E2E_USER_ID to the userId the journey identifies as}"
: "${E2E_FEEDBACK:?set E2E_FEEDBACK to the open-text answer the journey types}"
repo_root="$(cd "${E2E_ANDROID_DIR}/../.." && pwd)"
report_dir="${E2E_REPORT_DIR:-${E2E_ANDROID_DIR}/reports}"
results_dir="${repo_root}/playground/build/outputs/androidTest-results/connected"

"${E2E_ANDROID_DIR}/install-ca.sh"

gradle_args=()
while IFS= read -r line; do gradle_args+=("${line}"); done < <(
  journey_gradle_args "${E2E_EMULATOR_URL}" "${E2E_WORKSPACE_ID}" "${E2E_USER_ID}" "${E2E_FEEDBACK}"
)

attempt() {
  local n="$1" status=0
  log "Golden journey, attempt ${n}"
  adb logcat -c || true
  (cd "${repo_root}" && ./gradlew --console=plain "${gradle_args[@]}") || status=$?
  mkdir -p "${report_dir}/attempt-${n}"
  adb logcat -d -v threadtime >"${report_dir}/attempt-${n}/logcat.txt" 2>&1 || true
  [[ -d "${results_dir}" ]] && cp -R "${results_dir}/." "${report_dir}/attempt-${n}/" || true
  return "${status}"
}

if attempt 1; then
  exit 0
fi
printf '::warning::%s\n' "The golden journey failed on the first attempt; retrying once."
attempt 2
