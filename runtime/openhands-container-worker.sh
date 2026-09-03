#!/bin/sh
set -eu

if [ "${OPENHANDS_PROVIDER}" = "GEMINI" ]; then
  node /app/runtime/gemini-stream-proxy.mjs &
  bridge_pid=$!
  trap 'kill "$bridge_pid" 2>/dev/null || true' EXIT INT TERM
  attempt=0
  until curl --fail --silent "http://127.0.0.1:${GEMINI_PROXY_PORT}/health" >/dev/null; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 30 ] || ! kill -0 "$bridge_pid" 2>/dev/null; then
      echo "Gemini compatibility bridge did not become healthy." >&2
      exit 1
    fi
    sleep 1
  done
  export OPENHANDS_BASE_URL="http://127.0.0.1:${GEMINI_PROXY_PORT}"
fi

exec python3 /app/runtime/openhands_worker.py "$@"
