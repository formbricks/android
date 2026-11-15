#!/usr/bin/env bash
# Unit tests for the pure functions in e2e/android/lib.sh. Needs bash and jq only.
# Run: e2e/android/test/lib.test.sh
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source-path=SCRIPTDIR source=../lib.sh
source "${HERE}/../lib.sh"

failures=0
passes=0

pass() { passes=$((passes + 1)); }
fail() {
  failures=$((failures + 1))
  printf 'FAIL: %s\n' "$1" >&2
  [[ $# -gt 1 ]] && printf '  %s\n' "${@:2}" >&2
  return 0
}

assert_eq() {
  if [[ "$2" == "$3" ]]; then pass; else fail "$1" "expected: $3" "actual:   $2"; fi
}

# --- percent_encode -------------------------------------------------------------------------------

assert_eq "percent_encode keeps unreserved characters" \
  "$(percent_encode "qa-user-123_4.5~6")" "qa-user-123_4.5~6"
assert_eq "percent_encode encodes spaces as %20, not +" \
  "$(percent_encode "Golden journey feedback 42")" "Golden%20journey%20feedback%2042"
assert_eq "percent_encode encodes a literal plus, quotes and shell metacharacters" \
  "$(percent_encode "a+b \"c\" 'd' \$e;f&g")" "a%2Bb%20%22c%22%20%27d%27%20%24e%3Bf%26g"

# --- journey_gradle_args --------------------------------------------------------------------------

expected_args="$(printf '%s\n' \
  ":playground:connectedDebugAndroidTest" \
  "-Pandroid.testInstrumentationRunnerArguments.formbricksUrl=https%3A%2F%2F10.0.2.2%3A3443" \
  "-Pandroid.testInstrumentationRunnerArguments.workspaceId=clsdkqaws00000000000000001" \
  "-Pandroid.testInstrumentationRunnerArguments.userId=qa-user-7-1" \
  "-Pandroid.testInstrumentationRunnerArguments.feedback=Golden%20journey%20feedback%207")"
assert_eq "journey_gradle_args passes every journey input as a percent-encoded runner argument" \
  "$(journey_gradle_args "https://10.0.2.2:3443" "clsdkqaws00000000000000001" "qa-user-7-1" "Golden journey feedback 7")" \
  "${expected_args}"

if journey_gradle_args "https://10.0.2.2:3443" "clsdkqaws00000000000000001" "" "feedback" >/dev/null 2>&1; then
  fail "journey_gradle_args rejects an empty userId"
else
  pass
fi
if journey_gradle_args "https://10.0.2.2:3443" "clsdkqaws00000000000000001" "qa-user" "" >/dev/null 2>&1; then
  fail "journey_gradle_args rejects empty feedback"
else
  pass
fi

printf '%d passed, %d failed\n' "${passes}" "${failures}"
[[ "${failures}" -eq 0 ]]
