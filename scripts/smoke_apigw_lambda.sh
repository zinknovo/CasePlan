#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-https://mc94chabh2.execute-api.us-east-2.amazonaws.com}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-30}"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local out_body="$tmp_dir/body.json"
  local out_code
  if [[ -n "$body" ]]; then
    out_code=$(curl -sS --max-time "$TIMEOUT_SECONDS" -o "$out_body" -w "%{http_code}" \
      -X "$method" "$API_BASE_URL$path" \
      -H "content-type: application/json" \
      -d "$body")
  else
    out_code=$(curl -sS --max-time "$TIMEOUT_SECONDS" -o "$out_body" -w "%{http_code}" \
      -X "$method" "$API_BASE_URL$path")
  fi
  echo "$out_code"
}

assert_code() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "FAILED: $label expected HTTP $expected, got $actual"
    echo "Body: $(cat "$tmp_dir/body.json")"
    exit 1
  fi
}

json_get() {
  local key="$1"
  python3 - "$tmp_dir/body.json" "$key" <<'PY'
import json, sys
path, key = sys.argv[1], sys.argv[2]
with open(path, "r", encoding="utf-8") as f:
    data = json.load(f)
value = data.get(key)
print("" if value is None else value)
PY
}

echo "[1/3] POST /orders"
run_id="$(date +%s)"
post_body="$(cat <<JSON
{"clientFirstName":"Smoke","clientLastName":"Glue","attorneyName":"Smoke Atty","barNumber":"BAR-SMOKE-${run_id}","primaryCauseOfAction":"Contract","remedySought":"Compensation"}
JSON
)"
code="$(request POST "/orders" "$post_body")"
assert_code "$code" "201" "POST /orders"
order_id="$(json_get id)"
if [[ -z "$order_id" ]]; then
  echo "FAILED: POST /orders did not return id"
  echo "Body: $(cat "$tmp_dir/body.json")"
  exit 1
fi
echo "OK: created order id=$order_id"

echo "[2/3] GET /orders/{id}"
code="$(request GET "/orders/${order_id}")"
assert_code "$code" "200" "GET /orders/{id}"
status="$(json_get status)"
case "$status" in
  pending|processing|completed|failed) ;;
  *)
    echo "FAILED: unexpected status '$status'"
    echo "Body: $(cat "$tmp_dir/body.json")"
    exit 1
    ;;
esac
echo "OK: status=$status"

echo "[3/3] GET /orders/999999999 (not found)"
code="$(request GET "/orders/999999999")"
assert_code "$code" "404" "GET /orders/999999999"
echo "OK: not-found behavior valid"

echo "SMOKE PASS: API Gateway -> Lambda -> DB/SQS glue looks healthy."
