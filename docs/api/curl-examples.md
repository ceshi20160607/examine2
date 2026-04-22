## curl 联调示例（本地）

> 说明：本文件用于快速联调，参数与鉴权以你本地环境为准（token、host、platId、systemId/tenantId 等）。

### 基础变量

```bash
# 按需修改
export HOST="http://localhost:8080"
export TOKEN="REPLACE_ME"
export AUTH="Authorization: Bearer ${TOKEN}"
```

### 自建系统态：module 元数据

#### 元数据（meta）

```bash
# list apps
curl -s "${HOST}/v1/system/module/meta/apps" -H "${AUTH}"

# upsert app
curl -s "${HOST}/v1/system/module/meta/apps/upsert" -H "${AUTH}" -H "Content-Type: application/json" -d '{
  "appCode":"demo_app",
  "appName":"演示应用",
  "status":1
}'
```

#### 字典（dicts）

```bash
# list dicts by appId
curl -s "${HOST}/v1/system/module/dicts/apps/1" -H "${AUTH}"
```

#### 列表视图（list-views）

```bash
# list views by modelId
curl -s "${HOST}/v1/system/module/list-views/models/1" -H "${AUTH}"
```

#### 导出模板（exports）

```bash
# list export templates by modelId
curl -s "${HOST}/v1/system/module/exports/models/1/tpls" -H "${AUTH}"
```

#### module RBAC（rbac）

```bash
# list roles by appId
curl -s "${HOST}/v1/system/module/rbac/apps/1/roles" -H "${AUTH}"
```

### flow：节点配置约定速记

- **subflow**：节点 `type=subflow`，配置 `config.sub_temp_code`（必填），可选：
  - `config.copy_vars`（bool，父→子复制 vars）
  - `config.out_vars`（数组，子→父回写同名 varKey 列表）
  - `config.out_var_map`（对象，子→父回写映射：`{ "childKey":"parentKey" }`）
- **cc**：节点 `type=cc`，配置 `config.plat_ids`（数组；为空回退发起人）
- **会签**：节点 `type=approve`，配置 `config.sign_mode=all` + `config.plat_ids`
- **或签**：节点 `type=approve`，配置 `config.sign_mode=any` + `config.plat_ids`

### flow：系统态调用链（/v1/system/flow）

```bash
# 发起（defCode=un_flow_temp.temp_code，例如 demo_parent_subflow / demo_cc / demo_countersign_all / demo_any_sign）
curl -s "${HOST}/v1/system/flow/instances/start" -H "${AUTH}" -H "Content-Type: application/json" -d '{
  "defCode":"demo_parent_subflow",
  "bizType":"module:app:1:model:1",
  "bizId":"BIZ-001",
  "title":"测试单据",
  "vars":{"amount":100}
}'
```

发起成功会返回：
- `instanceId`：实例（`un_flow_record.id`）
- `taskId`：首个待办（`un_flow_task.id`）

```bash
# 同意（instanceId/taskId 替换为上一步返回）
curl -s "${HOST}/v1/system/flow/instances/REPLACE_INSTANCE_ID/tasks/REPLACE_TASK_ID/approve" \
  -H "${AUTH}" -H "Content-Type: application/json" -d '{"commentText":"同意"}'

# 拒绝（直接终态；会取消同节点其他待办）
curl -s "${HOST}/v1/system/flow/instances/REPLACE_INSTANCE_ID/tasks/REPLACE_TASK_ID/reject" \
  -H "${AUTH}" -H "Content-Type: application/json" -d '{"commentText":"拒绝"}'

# 撤回（发起人）
curl -s "${HOST}/v1/system/flow/instances/REPLACE_INSTANCE_ID/withdraw" -H "${AUTH}"

# 终止（发起人）
curl -s "${HOST}/v1/system/flow/instances/REPLACE_INSTANCE_ID/terminate" \
  -H "${AUTH}" -H "Content-Type: application/json" -d '{"reason":"终止原因"}'

# 转交（当前处理人）
curl -s "${HOST}/v1/system/flow/instances/REPLACE_INSTANCE_ID/tasks/REPLACE_TASK_ID/transfer" \
  -H "${AUTH}" -H "Content-Type: application/json" -d '{"toPlatId":10001}'

# 领取/取消领取（领取后仅领取人可同意/拒绝；并发保护）
curl -s "${HOST}/v1/system/flow/instances/REPLACE_INSTANCE_ID/tasks/REPLACE_TASK_ID/claim" -H "${AUTH}"
curl -s "${HOST}/v1/system/flow/instances/REPLACE_INSTANCE_ID/tasks/REPLACE_TASK_ID/unclaim" -H "${AUTH}"
```

### flow：系统态查询（按业务键 / 我的待办）

```bash
# 按 bizType + bizId 查询最新实例（可选 onlyRunning=1）
curl -s "${HOST}/v1/system/flow/instances/by-biz?bizType=module:app:1:model:1&bizId=BIZ-001&onlyRunning=1" \
  -H "${AUTH}"

# 实例分页列表（可选 keyword/startFrom/startTo）
curl -s "${HOST}/v1/system/flow/instances/page?page=1&size=20&status=1&rootOnly=1&tempId=REPLACE_TEMP_ID&currentNodeKey=approve1&keyword=BIZ&startFrom=2026-04-21T00:00:00" -H "${AUTH}"

# 我的相关实例分页（我发起 + 我待办 + 我抄送 聚合）
curl -s "${HOST}/v1/system/flow/instances/my/page?page=1&size=20&onlyRunning=1&rootOnly=1&tempId=REPLACE_TEMP_ID&currentNodeKey=approve1&keyword=BIZ" -H "${AUTH}"

# 任务分页列表（可选 nodeKey/keyword/createFrom/createTo）
curl -s "${HOST}/v1/system/flow/tasks/page?page=1&size=20&status=1&nodeKey=approve1&keyword=审批&createFrom=2026-04-21T00:00:00" -H "${AUTH}"

# 按 bizType + bizId 查询实例 + 当前待办（返回 record + pendingTasks）
curl -s "${HOST}/v1/system/flow/instances/by-biz/with-pending-tasks?bizType=module:app:1:model:1&bizId=BIZ-001" \
  -H "${AUTH}"

# 按 bizType + bizId 查询当前用户可办理的待办（用于“多待办=409”时辅助选择 taskId）
curl -s "${HOST}/v1/system/flow/instances/by-biz/actionable-tasks?bizType=module:app:1:model:1&bizId=BIZ-001" \
  -H "${AUTH}"

# 子流程实例列表（parentRecordId=instanceId）
curl -s "${HOST}/v1/system/flow/instances/REPLACE_INSTANCE_ID/children" -H "${AUTH}"

# 根单据下所有子实例（rootRecordId=instanceId 或 rootRecordId=record.rootRecordId）
curl -s "${HOST}/v1/system/flow/instances/REPLACE_INSTANCE_ID/tree" -H "${AUTH}"

# 我的待办（含或签候选）
curl -s "${HOST}/v1/system/flow/inbox/tasks/pending?limit=50" -H "${AUTH}"

# 我的抄送（cc；onlyUnread=1 仅未读）
curl -s "${HOST}/v1/system/flow/inbox/cc?limit=50&onlyUnread=1" -H "${AUTH}"

# 抄送标记已读
curl -s "${HOST}/v1/system/flow/inbox/cc/REPLACE_CC_TASK_ID/read" -H "${AUTH}" -X POST

# 按业务键一键同意/拒绝（自动定位当前用户可办待办；多待办会返回 409）
curl -s "${HOST}/v1/system/flow/instances/by-biz/approve" \
  -H "${AUTH}" -H "Content-Type: application/json" -d '{
  "bizType":"module:app:1:model:1",
  "bizId":"BIZ-001",
  "taskId":123,
  "commentText":"同意"
}'

curl -s "${HOST}/v1/system/flow/instances/by-biz/reject" \
  -H "${AUTH}" -H "Content-Type: application/json" -d '{
  "bizType":"module:app:1:model:1",
  "bizId":"BIZ-001",
  "taskId":123,
  "commentText":"拒绝"
}'
```

### 开放 API（/v1/open/**）：AK/SK + 代操作 + 幂等

```bash
export AK="REPLACE_AK"
export SK="REPLACE_SK"
export ACTING_PLAT_ID="REPLACE_PLAT_ID"

# 平台级 client 需指定目标 system：export TARGET_SYSTEM_ID="REPLACE_SYSTEM_ID"

export OPEN_HEADERS_AKSK=(
  -H "X-Access-Key: ${AK}"
  -H "X-Secret: ${SK}"
  -H "X-Acting-Plat-Id: ${ACTING_PLAT_ID}"
)

# 平台级 client（system_id=0）额外头：
#   -H "X-Target-System-Id: ${TARGET_SYSTEM_ID}"
# 可选：
#   -H "X-Target-Tenant-Id: ${TARGET_TENANT_ID}"
```

#### OpenAPI：发起 flow（幂等）

```bash
curl -s "${HOST}/v1/open/flow/instances/start" \
  "${OPEN_HEADERS_AKSK[@]}" \
  -H "Idempotency-Key: idem-001" \
  -H "Content-Type: application/json" -d '{
  "tempCode":"demo_cc",
  "bizType":"module:app:1:model:1",
  "bizId":"EXT-BIZ-001",
  "title":"外部发起",
  "vars":{"amount":100}
}'
```

#### OpenAPI：办理（同意/拒绝/撤回/终止/转交，均可 Idempotency-Key）

```bash
curl -s "${HOST}/v1/open/flow/instances/REPLACE_INSTANCE_ID/tasks/REPLACE_TASK_ID/approve" \
  "${OPEN_HEADERS_AKSK[@]}" \
  -H "Idempotency-Key: idem-approve-001" \
  -H "Content-Type: application/json" -d '{"commentText":"ok"}'

# 领取/取消领取（可选 Idempotency-Key）
curl -s "${HOST}/v1/open/flow/instances/REPLACE_INSTANCE_ID/tasks/REPLACE_TASK_ID/claim" \
  "${OPEN_HEADERS_AKSK[@]}" \
  -H "Idempotency-Key: idem-claim-001"

curl -s "${HOST}/v1/open/flow/instances/REPLACE_INSTANCE_ID/tasks/REPLACE_TASK_ID/unclaim" \
  "${OPEN_HEADERS_AKSK[@]}" \
  -H "Idempotency-Key: idem-unclaim-001"
```

#### OpenAPI：查询（按业务键 / 我的待办）

```bash
# 按 bizType + bizId 查询最新实例
curl -s "${HOST}/v1/open/flow/instances/by-biz?bizType=module:app:1:model:1&bizId=EXT-BIZ-001&onlyRunning=1" \
  "${OPEN_HEADERS_AKSK[@]}"

# 实例分页列表（可选 keyword/startFrom/startTo）
curl -s "${HOST}/v1/open/flow/instances/page?page=1&size=20&status=1&rootOnly=1&tempId=REPLACE_TEMP_ID&currentNodeKey=approve1&keyword=EXT&startFrom=2026-04-21T00:00:00" \
  "${OPEN_HEADERS_AKSK[@]}"

# 我的相关实例分页（开放 API；我发起 + 我待办 + 我抄送 聚合）
curl -s "${HOST}/v1/open/flow/instances/my/page?page=1&size=20&onlyRunning=1&rootOnly=1&tempId=REPLACE_TEMP_ID&currentNodeKey=approve1&keyword=EXT" \
  "${OPEN_HEADERS_AKSK[@]}"

# 任务分页列表（可选 nodeKey/keyword/createFrom/createTo）
curl -s "${HOST}/v1/open/flow/tasks/page?page=1&size=20&status=1&nodeKey=approve1&keyword=审批&createFrom=2026-04-21T00:00:00" \
  "${OPEN_HEADERS_AKSK[@]}"

# 按 bizType + bizId 查询实例 + 当前待办
curl -s "${HOST}/v1/open/flow/instances/by-biz/with-pending-tasks?bizType=module:app:1:model:1&bizId=EXT-BIZ-001" \
  "${OPEN_HEADERS_AKSK[@]}"

# 按 bizType + bizId 查询当前用户可办理的待办（用于“多待办=409”时辅助选择 taskId）
curl -s "${HOST}/v1/open/flow/instances/by-biz/actionable-tasks?bizType=module:app:1:model:1&bizId=EXT-BIZ-001" \
  "${OPEN_HEADERS_AKSK[@]}"

# 子流程实例列表（parentRecordId=instanceId）
curl -s "${HOST}/v1/open/flow/instances/REPLACE_INSTANCE_ID/children" \
  "${OPEN_HEADERS_AKSK[@]}"

# 根单据下所有子实例（rootRecordId=instanceId 或 rootRecordId=record.rootRecordId）
curl -s "${HOST}/v1/open/flow/instances/REPLACE_INSTANCE_ID/tree" \
  "${OPEN_HEADERS_AKSK[@]}"

# 我的待办（开放 API；含或签候选）
curl -s "${HOST}/v1/open/flow/inbox/tasks/pending?limit=50" \
  "${OPEN_HEADERS_AKSK[@]}"

# 我的抄送（cc）
curl -s "${HOST}/v1/open/flow/inbox/cc?limit=50&onlyUnread=1" \
  "${OPEN_HEADERS_AKSK[@]}"

# 抄送标记已读（可选 Idempotency-Key）
curl -s "${HOST}/v1/open/flow/inbox/cc/REPLACE_CC_TASK_ID/read" \
  "${OPEN_HEADERS_AKSK[@]}" \
  -H "Idempotency-Key: idem-cc-read-001" \
  -X POST

# 按业务键一键同意/拒绝（可选 Idempotency-Key）
curl -s "${HOST}/v1/open/flow/instances/by-biz/approve" \
  "${OPEN_HEADERS_AKSK[@]}" \
  -H "Idempotency-Key: idem-biz-approve-001" \
  -H "Content-Type: application/json" -d '{
  "bizType":"module:app:1:model:1",
  "bizId":"EXT-BIZ-001",
  "taskId":123,
  "commentText":"ok"
}'

curl -s "${HOST}/v1/open/flow/instances/by-biz/reject" \
  "${OPEN_HEADERS_AKSK[@]}" \
  -H "Idempotency-Key: idem-biz-reject-001" \
  -H "Content-Type: application/json" -d '{
  "bizType":"module:app:1:model:1",
  "bizId":"EXT-BIZ-001",
  "taskId":123,
  "commentText":"no"
}'
```

#### OpenAPI：业务记录（EAV）

```bash
# 创建 record（返回 recordId）
curl -s "${HOST}/v1/open/records" \
  "${OPEN_HEADERS_AKSK[@]}" \
  -H "Idempotency-Key: idem-record-001" \
  -H "Content-Type: application/json" -d '{
  "appId": 1,
  "modelId": 1,
  "data": {
    "name": "张三",
    "amount": 100
  }
}'
```

