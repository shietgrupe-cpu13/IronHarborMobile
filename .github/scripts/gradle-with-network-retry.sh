#!/usr/bin/env bash
# Retry only transient dependency-resolution/transport failures. Compilation,
# test, and other deterministic Gradle failures are returned immediately.
set -uo pipefail

if [[ -z "${GITHUB_WORKSPACE:-}" || ! -x "$GITHUB_WORKSPACE/android/gradlew" ]]; then
  echo "Gradle wrapper not found under GITHUB_WORKSPACE=$GITHUB_WORKSPACE" >&2
  exit 2
fi

for attempt in 1 2 3; do
  log="${RUNNER_TEMP:-/tmp}/gradle-attempt-${GITHUB_RUN_ID:-local}-${attempt}.log"
  set +e
  "$GITHUB_WORKSPACE/android/gradlew" "$@" 2>&1 | tee "$log"
  status=${PIPESTATUS[0]}
  set -e

  if [[ "$status" -eq 0 ]]; then
    exit 0
  fi

  if ! grep -Eiq \
    'status code (429|5[0-9][0-9]) from server|HTTP/(1\.[01]|2) (429|5[0-9][0-9])|UnknownHostException|Temporary failure in name resolution|Connection reset|Connection refused|ConnectException|SocketTimeoutException|Read timed out|No route to host' \
    "$log"; then
    echo "Gradle failed without a recognized transient dependency/network error; not retrying." >&2
    exit "$status"
  fi

  if [[ "$attempt" -eq 3 ]]; then
    echo "Gradle still failed after three transient-error attempts." >&2
    exit "$status"
  fi

  delay=$((attempt * 30))
  echo "Transient Gradle dependency/network failure; retrying in ${delay}s."
  sleep "$delay"
done

exit 1
