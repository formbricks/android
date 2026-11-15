#!/usr/bin/env bash
# Shared constants and functions for the Android side of the SDK E2E harness. Source it; do not run
# it. The server itself is e2e/server (copied unchanged from the js repo).
# Dependencies: bash, jq, openssl, docker, adb.
# shellcheck disable=SC2034 # constants are read by the scripts that source this file

# The SDK only accepts an https:// appUrl (Formbricks.setup), so the emulator reaches the server
# through a TLS proxy on the runner: 10.0.2.2 is the emulator's alias for the host's loopback.
E2E_HTTPS_PORT="${E2E_HTTPS_PORT:-3443}"
E2E_EMULATOR_URL="${E2E_EMULATOR_URL:-https://10.0.2.2:${E2E_HTTPS_PORT}}"
# Where the proxy forwards to: the port e2e/server/docker-compose.yml publishes.
E2E_UPSTREAM="${E2E_UPSTREAM:-127.0.0.1:3000}"

E2E_ANDROID_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# The throwaway CA, the server certificate and the proxy config. Never committed.
E2E_TLS_DIR="${E2E_TLS_DIR:-${E2E_ANDROID_DIR}/.state/tls}"
E2E_PROXY_CONTAINER="formbricks-sdk-e2e-https"
E2E_PROXY_IMAGE="nginx:1.30-alpine@sha256:901e593c95dc85f5a729f5765516321366ef4ac401821431d408897e95287d0a"

# --- pure -----------------------------------------------------------------------------------------

# percent_encode <value>: RFC 3986 encoding (space is %20, not +).
percent_encode() {
  jq -rn --arg v "$1" '$v | @uri'
}

# journey_gradle_args <appUrl> <workspaceId> <userId> <feedback>
# Prints the Gradle arguments that run the golden journey, one per line. Every input goes in as a
# percent-encoded instrumentation argument: the test runner splits `am instrument -e` values on
# spaces, and the feedback has spaces. GoldenJourneyTest decodes them.
journey_gradle_args() {
  local url="$1" workspace="$2" user="$3" feedback="$4" prefix="-Pandroid.testInstrumentationRunnerArguments"
  [[ -n "${url}" && -n "${workspace}" && -n "${user}" && -n "${feedback}" ]] || {
    echo "journey_gradle_args needs appUrl, workspaceId, userId and feedback" >&2
    return 1
  }
  printf '%s\n' \
    ":playground:connectedDebugAndroidTest" \
    "${prefix}.formbricksUrl=$(percent_encode "${url}")" \
    "${prefix}.workspaceId=$(percent_encode "${workspace}")" \
    "${prefix}.userId=$(percent_encode "${user}")" \
    "${prefix}.feedback=$(percent_encode "${feedback}")"
}

# --- I/O helpers ----------------------------------------------------------------------------------

log() { printf '==> %s\n' "$*" >&2; }

die() {
  printf '::error::%s\n' "$*" >&2
  exit 1
}
