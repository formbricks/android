#!/usr/bin/env bash
# Tests for pr-check-summary.sh. Run: .github/scripts/pr-check-summary.test.sh
set -uo pipefail

script="$(dirname "$0")/pr-check-summary.sh"
failures=0

# expect <description> <expected exit code> <event> <is fork PR> <needs JSON>
expect() {
  local description="$1" expected="$2" event="$3" fork="$4" needs="$5" output status
  output="$(EVENT_NAME="$event" IS_FORK_PR="$fork" NEEDS_JSON="$needs" "$script" 2>&1)"
  status=$?
  if [[ "$status" -ne "$expected" ]]; then
    echo "FAIL: $description (exit $status, expected $expected)"
    echo "$output" | sed 's/^/    /'
    failures=$((failures + 1))
  else
    echo "ok: $description"
  fi
}

expect "every job succeeded" 0 pull_request false \
  '{"lint":{"result":"success"},"build":{"result":"success"}}'
expect "a failed job fails the summary" 1 pull_request false \
  '{"lint":{"result":"success"},"build":{"result":"failure"}}'

expect "a cancelled job fails the summary" 1 pull_request false \
  '{"lint":{"result":"cancelled"}}'
expect "an unexpected skip fails the summary" 1 pull_request false \
  '{"lint":{"result":"skipped"}}'

expect "sonarqube may skip on a fork PR" 0 pull_request true \
  '{"lint":{"result":"success"},"sonarqube":{"result":"skipped"}}'

expect "e2e-tests may skip on a fork PR" 0 pull_request true \
  '{"lint":{"result":"success"},"e2e-tests":{"result":"skipped"}}'

expect "sonarqube may not skip on a same-repo PR" 1 pull_request false \
  '{"sonarqube":{"result":"skipped"}}'

expect "sonarqube may not skip in the merge queue" 1 merge_group false \
  '{"sonarqube":{"result":"skipped"}}'

expect "a fork PR may still not skip other jobs" 1 pull_request true \
  '{"build":{"result":"skipped"}}'

expect "dependency-review may skip on a push to main" 0 push false \
  '{"lint":{"result":"success"},"dependency-review":{"result":"skipped"}}'

expect "dependency-review may not skip on a PR" 1 pull_request false \
  '{"dependency-review":{"result":"skipped"}}'

if [[ "$failures" -gt 0 ]]; then
  echo "$failures test(s) failed"
  exit 1
fi
