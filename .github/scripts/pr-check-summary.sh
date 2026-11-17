#!/usr/bin/env bash
# The `PR Check Summary` gate: the one required check. Passes only when every job it needs
# succeeded, or was skipped where skipping is expected:
#   - sonarqube and e2e-tests on a PR from a fork, which gets no secrets;
#   - dependency-review on a push to main, where there is no diff to review.
# Anything else - failure, cancellation, or a skip outside those cases - fails it.
#
# Env: NEEDS_JSON (`toJSON(needs)`), EVENT_NAME (`github.event_name`),
#      IS_FORK_PR ("true" for a PR whose head lives in another repository).
# Tests: pr-check-summary.test.sh
set -euo pipefail

allowed_skips=()
if [[ "$EVENT_NAME" == "pull_request" && "$IS_FORK_PR" == "true" ]]; then
  allowed_skips+=(sonarqube e2e-tests)
fi
if [[ "$EVENT_NAME" == "push" ]]; then
  allowed_skips+=(dependency-review)
fi

allowed_json="$(printf '%s\n' "${allowed_skips[@]+"${allowed_skips[@]}"}" | jq -R . | jq -cs 'map(select(. != ""))')"

bad="$(jq -r --argjson allowed "$allowed_json" '
  to_entries[]
  | select(.value.result != "success")
  | select(.value.result != "skipped" or (.key | IN($allowed[]) | not))
  | "  \(.key): \(.value.result)"
' <<<"$NEEDS_JSON")"

if [[ -n "$bad" ]]; then
  echo "These checks did not pass:"
  echo "$bad"
  exit 1
fi
echo "All checks passed."
