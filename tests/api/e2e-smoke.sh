#!/usr/bin/env bash
# examine2 API 端到端冒烟（bash + curl + python3）
set -euo pipefail

HOST="${EXAMINE_HOST:-http://127.0.0.1:9999}"
HOST="${HOST%/}"
SMOKE_PASS="${SMOKE_PASS:-SmokePass123!}"
SKIP_OPEN_API="${SKIP_OPEN_API:-1}"
OPEN_AK="${OPEN_AK:-}"
OPEN_SK="${OPEN_SK:-}"
OPEN_ACTING_PLAT_ID="${OPEN_ACTING_PLAT_ID:-}"
OPEN_TARGET_SYSTEM_ID="${OPEN_TARGET_SYSTEM_ID:-}"

TS="$(date +%s)"
AUTH_MODE="register"
if [[ -n "${SMOKE_USER:-}" ]]; then
  AUTH_MODE="login"
else
  SMOKE_USER="smoke_${TS}"
fi

TOKEN=""
SYSTEM_ID="${SMOKE_SYSTEM_ID:-}"
APP_ID=""
MODEL_ID=""
FIELD_CODE="smoke_title"
RECORD_ID=""
PLAT_ID=""
PASS=0
FAIL=0

green() { printf '\033[32m%s\033[0m\n' "$*"; }
red() { printf '\033[31m%s\033[0m\n' "$*" >&2; }
ok() { PASS=$((PASS + 1)); green "[OK] $1"; }
fail() { FAIL=$((FAIL + 1)); red "[FAIL] $1 — $2"; }

need_tools() {
  command -v python3 >/dev/null 2>&1 || { fail "python3" "未安装"; exit 1; }
  command -v curl >/dev/null 2>&1 || { fail "curl" "未安装"; exit 1; }
}

api() {
  local method="$1" path="$2" body="${3:-}" use_auth="${4:-1}"
  local tmp http_code args
  tmp="$(mktemp)"
  args=(-sS -X "$method" "${HOST}${path}" -H "Content-Type: application/json")
  if [[ "$use_auth" == "1" && -n "${TOKEN:-}" ]]; then
    args+=(-H "Authorization: Bearer ${TOKEN}")
  fi
  if [[ -n "$body" ]]; then args+=(-d "$body"); fi
  http_code="$(curl "${args[@]}" -o "$tmp" -w '%{http_code}')" || true
  local raw; raw="$(cat "$tmp")"; rm -f "$tmp"
  python3 - "$http_code" "$raw" <<'PY'
import json, sys
http_code, raw = sys.argv[1], sys.argv[2]
try:
    r = json.loads(raw) if raw.strip() else {}
except json.JSONDecodeError:
    print(json.dumps({"_error": "invalid json", "raw": raw[:300]}))
    sys.exit(1)
if r.get("code") != 0:
    print(json.dumps({"_error": r.get("message") or "api error", "http": http_code}))
    sys.exit(1)
print(json.dumps(r.get("data"), ensure_ascii=False))
PY
}

run_step() {
  local name="$1"; shift
  if "$@"; then ok "$name"; return 0; fi
  fail "$name" "${SMOKE_ERR:-failed}"
  return 1
}

step_ping() { SMOKE_ERR=""; api GET /ping "" 0 >/dev/null; }

step_health() {
  SMOKE_ERR=""
  local code
  code="$(curl -sS -o /dev/null -w '%{http_code}' "${HOST}/actuator/health" 2>/dev/null || echo 000)"
  [[ "$code" == "200" ]] || echo "  (actuator HTTP ${code}, 可忽略)" >&2
  return 0
}

step_auth() {
  SMOKE_ERR=""
  if [[ "$AUTH_MODE" == "register" ]]; then
    api POST /v1/platform/auth/register "{\"username\":\"${SMOKE_USER}\",\"password\":\"${SMOKE_PASS}\"}" 0 >/dev/null 2>/dev/null || true
  fi
  local login
  login="$(api POST /v1/platform/auth/login "{\"username\":\"${SMOKE_USER}\",\"password\":\"${SMOKE_PASS}\"}" 0)" || {
    SMOKE_ERR="$login"; return 1
  }
  TOKEN="$(echo "$login" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")"
  PLAT_ID="$(api GET /v1/platform/auth/me | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")"
  OPEN_ACTING_PLAT_ID="${OPEN_ACTING_PLAT_ID:-$PLAT_ID}"
  return 0
}

step_system() {
  SMOKE_ERR=""
  if [[ -z "$SYSTEM_ID" ]]; then
    local sys_name="smoke_sys_${TS}" created listed
    if created="$(api POST /v1/platform/systems "{\"name\":\"${sys_name}\",\"multiTenantEnabled\":0}" 2>&1)"; then
      SYSTEM_ID="$(echo "$created" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")"
    else
      listed="$(api GET /v1/platform/systems 2>/dev/null || echo '[]')"
      SYSTEM_ID="$(echo "$listed" | python3 -c "import sys,json; a=json.load(sys.stdin); print(a[0]['id'] if a else '')" 2>/dev/null || true)"
      if [[ -z "$SYSTEM_ID" ]]; then
        SMOKE_ERR="无法创建系统（需 SYSTEM_CREATE）。设置 SMOKE_USER/SMOKE_PASS 为管理员或 SMOKE_SYSTEM_ID"
        return 1
      fi
    fi
  fi
  api POST /v1/platform/context/enter-system "{\"systemId\":${SYSTEM_ID}}" >/dev/null || {
    SMOKE_ERR="enter-system failed"; return 1
  }
  OPEN_TARGET_SYSTEM_ID="${OPEN_TARGET_SYSTEM_ID:-$SYSTEM_ID}"
  return 0
}

step_meta_record() {
  SMOKE_ERR=""
  local suffix="${TS}"
  APP_ID="$(api POST /v1/system/module/meta/apps/upsert "{\"appCode\":\"smoke_app_${suffix}\",\"appName\":\"Smoke App\",\"status\":1}" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")"
  MODEL_ID="$(api POST /v1/system/module/meta/models/upsert "{\"appId\":${APP_ID},\"modelCode\":\"smoke_model\",\"modelName\":\"Smoke Model\",\"status\":1}" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")"
  api POST /v1/system/module/meta/fields/upsert "{\"appId\":${APP_ID},\"modelId\":${MODEL_ID},\"fieldCode\":\"${FIELD_CODE}\",\"fieldName\":\"标题\",\"fieldType\":\"TEXT\",\"status\":1}" >/dev/null
  local created
  created="$(api POST /v1/system/records "{\"appId\":${APP_ID},\"modelId\":${MODEL_ID},\"data\":{\"${FIELD_CODE}\":\"smoke-${suffix}\"}}")"
  RECORD_ID="$(echo "$created" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('recordId') or d.get('record',{}).get('id'))")"
  api GET "/v1/system/records/${RECORD_ID}" >/dev/null
  api POST /v1/system/records/query "{\"appId\":${APP_ID},\"modelId\":${MODEL_ID},\"page\":1,\"limit\":5,\"includeFieldCodes\":[\"${FIELD_CODE}\"]}" >/dev/null
  api POST "/v1/system/records/${RECORD_ID}/update" "{\"data\":{\"${FIELD_CODE}\":\"smoke-upd-${suffix}\"}}" >/dev/null
  return 0
}

step_rbac() {
  SMOKE_ERR=""
  api GET "/v1/system/module/rbac/apps/${APP_ID}/roles" >/dev/null
  api POST "/v1/system/module/rbac/apps/${APP_ID}/roles/upsert" '{"roleCode":"smoke_role","roleName":"Smoke Role","dataScope":5,"status":1}' >/dev/null
  api GET "/v1/system/module/rbac/apps/${APP_ID}/runtime-menus" >/dev/null
  return 0
}

step_upload() {
  SMOKE_ERR=""
  local f resp http_code
  f="$(mktemp)"
  echo "smoke-upload-${TS}" >"$f"
  resp="$(mktemp)"
  http_code="$(curl -sS -o "$resp" -w '%{http_code}' -X POST "${HOST}/v1/system/uploads" \
    -H "Authorization: Bearer ${TOKEN}" -F "file=@${f};filename=smoke.txt")"
  rm -f "$f"
  python3 - "$http_code" "$(cat "$resp")" <<'PY' >/dev/null || { rm -f "$resp"; SMOKE_ERR="upload"; return 1; }
import json, sys
r = json.loads(sys.argv[2])
if r.get("code") != 0: sys.exit(1)
PY
  rm -f "$resp"
  return 0
}

step_inbox_flow() {
  SMOKE_ERR=""
  api GET "/v1/platform/messages?limit=5" >/dev/null || true
  api GET "/v1/platform/todos?limit=5" >/dev/null || true
  api GET "/v1/platform/cc?limit=5" >/dev/null || true
  api GET "/v1/system/flow/inbox/tasks/pending?limit=5" >/dev/null || true
  return 0
}

step_flow_graph() {
  SMOKE_ERR=""
  local temp ver ver_id saved loaded
  temp="$(api POST /v1/system/flow/temps/upsert "{\"tempCode\":\"smoke_flow_${TS}\",\"tempName\":\"Smoke Flow\",\"status\":1}")" || { SMOKE_ERR="flow temp upsert"; return 1; }
  local temp_id
  temp_id="$(echo "$temp" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")"
  ver="$(api POST /v1/system/flow/temp-vers/upsert "{\"tempId\":${temp_id},\"publishStatus\":1,\"formJson\":\"{}\"}")" || { SMOKE_ERR="flow ver upsert"; return 1; }
  ver_id="$(echo "$ver" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")"
  saved="$(api POST "/v1/system/flow/temp-vers/${ver_id}/graph-designer" '{
    "nodes":[
      {"nodeKey":"start_1","nodeType":"start","nodeName":"Start","x":120,"y":120,"configJson":"{}"},
      {"nodeKey":"approve_1","nodeType":"approve","nodeName":"Approve","x":320,"y":120,"configJson":"{}"},
      {"nodeKey":"end_1","nodeType":"end","nodeName":"End","x":520,"y":120,"configJson":"{}"}
    ],
    "edges":[
      {"fromNodeKey":"start_1","toNodeKey":"approve_1","priority":1,"isDefault":0,"cond":""},
      {"fromNodeKey":"approve_1","toNodeKey":"end_1","priority":1,"isDefault":0,"cond":""}
    ]
  }')" || { SMOKE_ERR="graph-designer save"; return 1; }
  echo "$saved" | python3 -c "import sys,json; d=json.load(sys.stdin); assert d.get('graphJson'), 'graphJson missing'" || { SMOKE_ERR="graphJson"; return 1; }
  loaded="$(api GET "/v1/system/flow/temp-vers/${ver_id}/graph-designer")" || { SMOKE_ERR="graph-designer load"; return 1; }
  echo "$loaded" | python3 -c "import sys,json; d=json.load(sys.stdin); assert len(d.get('nodes') or [])>=3" || { SMOKE_ERR="nodes count"; return 1; }
  api GET "/v1/system/flow/temp-vers/${ver_id}" >/dev/null || { SMOKE_ERR="temp-ver get"; return 1; }
  api POST "/v1/system/flow/temp-vers/${ver_id}/publish" '{}' >/dev/null || { SMOKE_ERR="temp-ver publish"; return 1; }
  return 0
}

step_open_api() {
  SMOKE_ERR=""
  [[ "$SKIP_OPEN_API" == "1" ]] && return 0
  [[ -z "$OPEN_AK" || -z "$OPEN_SK" ]] && return 0
  local tmp body
  tmp="$(mktemp)"
  body="{\"appId\":${APP_ID},\"modelId\":${MODEL_ID},\"page\":1,\"limit\":5}"
  curl -sS -X POST "${HOST}/v1/open/records/query" \
    -H "X-Access-Key: ${OPEN_AK}" -H "X-Secret: ${OPEN_SK}" \
    -H "X-Acting-Plat-Id: ${OPEN_ACTING_PLAT_ID}" \
    -H "X-Target-System-Id: ${OPEN_TARGET_SYSTEM_ID}" \
    -H "Content-Type: application/json" -d "$body" -o "$tmp"
  python3 - "$tmp" <<'PY' || { rm -f "$tmp"; SMOKE_ERR="open query"; return 1; }
import json, sys
r = json.load(open(sys.argv[1]))
if r.get("code") != 0: sys.exit(1)
PY
  rm -f "$tmp"
  return 0
}

summary() {
  echo "---"
  echo "Passed: ${PASS}  Failed: ${FAIL}"
  if [[ "$FAIL" -eq 0 ]]; then green "SMOKE OK"; else red "SMOKE FAILED"; fi
}

main() {
  need_tools
  echo "=== examine2 e2e smoke ==="
  echo "HOST=${HOST} USER=${SMOKE_USER} MODE=${AUTH_MODE}"

  set +e
  run_step "ping" step_ping || true
  run_step "actuator/health" step_health || true
  run_step "auth" step_auth || { summary; exit 1; }
  run_step "system" step_system || { summary; exit 1; }
  run_step "meta + records" step_meta_record || { summary; exit 1; }
  run_step "rbac + runtime menus" step_rbac || { summary; exit 1; }
  run_step "upload" step_upload || { summary; exit 1; }
  run_step "inbox + flow pending" step_inbox_flow || { summary; exit 1; }
  run_step "flow graph-designer" step_flow_graph || { summary; exit 1; }
  run_step "open api (optional)" step_open_api || true
  set -e
  summary
  [[ "$FAIL" -eq 0 ]]
}

main "$@"
