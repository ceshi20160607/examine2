## OpenAPI 契约（/v1/open/**）

本文件定义对外开放 API 的**鉴权、代操作、幂等与通用错误码**约定；与可运行的请求示例配合使用：
- `docs/api/curl-examples.md`

### 1) 路径与版本

- **前缀**：`/v1/open/**`
- **版本**：`v1` 作为路径的一段；兼容策略（弃用窗口、是否并行 v2）以后续发布说明为准

### 2) 鉴权（AK/SK 或 HMAC 签名）

**方式 A — 签名（推荐，不传 SK）**

| 头 | 说明 |
|---|---|
| `X-Access-Key` | AK |
| `X-Timestamp` | Unix 秒或毫秒时间戳（与服务器偏差 ≤ 300s） |
| `X-Signature` | `Base64(HMAC-SHA256(secret, canonical))` |
| `X-Signature-Version` | 可选，固定 `v1` |

`canonical` 拼接（UTF-8，换行 `\n`）：

```
HTTP_METHOD + "\n" + pathWithQuery + "\n" + timestamp + "\n" + hex(SHA256(body))
```

- `pathWithQuery` = `requestURI` + `?queryString`（无 query 则仅 URI）
- `body` 为空时 SHA256 取空字节数组

凭证须在创建/轮换 SK 后写入 `sign_secret_enc`；旧凭证无该字段时需 **轮换 SK** 后方可走签名模式。

**方式 B — 明文 SK（兼容）**

- `X-Access-Key` + `X-Secret`，或 `Authorization: Basic base64(accessKey:secret)`

鉴权失败返回：
- HTTP 401 + 业务码（`ApiResult.code`）非 0

### 3) 代操作（Acting）

开放 API 通过请求头指定“以谁的身份操作”：

- `X-Acting-Plat-Id`: 代操作用户 platId（必填）

说明：
- 代操作产生的写入（动作日志、更新人等）应以该 platId 作为操作者

### 4) 目标 system/tenant

当对外应用属于平台级（`system_id=0`）时，需要显式指定目标系统/租户：

- `X-Target-System-Id`: 目标 systemId（必填）
- `X-Target-Tenant-Id`: 目标 tenantId（可选；无多租户时可省略，默认 0）

### 5) 幂等（Idempotency-Key）

对所有“可能产生写入”的接口（`POST/PUT/DELETE` 等）支持可选幂等键：

- `Idempotency-Key`: 任意非空字符串

规则：
- 成功响应（`ApiResult.code=0`）会缓存 **24h**
- 同 key 重放：返回与首次成功相同的响应，并追加响应头：
  - `X-Idempotency-Replay: 1`

注意：
- 幂等只缓存“成功响应”；失败不会缓存

### 6) 通用错误码（约定）

本项目统一使用 `ApiResult` 包装响应：

- `code=0`：成功
- `code!=0`：失败（通常与 HTTP status 对齐）

常见约定（以实际返回为准）：

- 400：参数错误
- 401：鉴权失败/未登录
- 403：无权限/未进入系统/未选择租户等上下文不满足
- 404：资源不存在
- 409：冲突（例如：一键办理存在多个可办待办；或资源状态不允许）

### 7) 业务记录（系统态与开放态）

**系统态** `/v1/system/records/**`：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/v1/system/records` | 创建 |
| GET | `/v1/system/records/{recordId}` | 详情 |
| POST | `/v1/system/records/{recordId}/update` | 更新 |
| DELETE | `/v1/system/records/{recordId}` | 软删 |
| POST | `/v1/system/records/query` | DSL 分页查询 |
| POST | `/v1/system/records/query-by-relation` | 按关系查子记录 |
| GET | `/v1/system/records/{recordId}/history` | 变更历史 |

**开放态** `/v1/open/records/**`（鉴权见 §2–§4）：与系统态能力对齐（create/detail/update/delete/query/query-by-relation/history）；写入类接口支持 `Idempotency-Key`。

`POST .../query` 与 `query-by-relation` 内 `query` 均支持可选 `includeFieldCodes`：在 `list[]` 每条记录上附带 `data`（field_code → value_text），避免 N+1 调 detail。

关联下拉、子表行展示（Web `refPicker.js` / 移动端 `refPicker.ts`）通过 `filters: [{ field:'id', op:'in', values:[...] }]` + `includeFieldCodes` 批量拉取。

### 8) Flow OpenAPI（当前已提供的能力）

flow 的 OpenAPI 入口位于：
- `/v1/open/flow/**`

涵盖：
- 发起、同意、拒绝、撤回、终止、转交
- 按 bizType/bizId 查询实例、查询任务/动作/轨迹
- 待办收件箱、抄送收件箱（含已读标记）
- “按业务键一键办理”与可选 taskId 精确办理
- 领取/取消领取（`claim` / `unclaim`）
- 分页查询：`/instances/page`、`/tasks/page`、`/instances/my/page`；常用筛选含 `keyword`、`startFrom/startTo`、`tempId`、`rootOnly=1`（仅顶层实例）、`currentNodeKey` 等

具体可运行示例见：
- `docs/api/curl-examples.md`

### 9) Flow 系统态（模板 / 可视化设计器）

**前缀**：`/v1/system/flow/**`（需 token 且已进入自建系统）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/v1/system/flow/temps/upsert` | 模板 `tempCode` / `tempName` |
| POST | `/v1/system/flow/temp-vers/upsert` | 版本草稿；可带 `graphJson` / `formJson` |
| GET | `/v1/system/flow/temp-vers/{tempVerId}` | 版本详情（含 `graphJson`） |
| GET | `/v1/system/flow/temp-vers/{tempVerId}/graph-designer` | 加载设计器：`nodes` / `edges` / `graphJson` |
| POST | `/v1/system/flow/temp-vers/{tempVerId}/graph-designer` | 保存设计器：写节点/边表并生成 `graphJson` |
| POST | `/v1/system/flow/temp-vers/{id}/publish` | 发布（要求已有非空 `graphJson`） |

**`POST .../graph-designer` 请求体**（与 Web `FlowGraphDesignerView`、移动端 `temp_ver_graph_edit` 一致）：

```json
{
  "nodes": [
    { "nodeKey": "start_1", "nodeType": "start", "nodeName": "开始", "x": 120, "y": 120, "configJson": "{}" }
  ],
  "edges": [
    { "fromNodeKey": "start_1", "toNodeKey": "approve_1", "priority": 1, "isDefault": 0, "cond": "" }
  ],
  "graphConfigJson": null
}
```

- `nodes[].configJson` 可含布局 `x`/`y` 及业务配置（如 subflow 的 `sub_temp_code`、approve 的 `plat_ids` / `sign_mode`）。
- `edges[].cond` 为条件表达式字符串；服务端会同步到连线条件表。
- 仅存在 `graphJson`、节点表为空时，**GET graph-designer** 会尝试从 `graphJson` 导入节点/边（MVP 形：`nodes[].id` / `edges[].from|to`）。

运行时实例、待办、办理等仍见 `curl-examples.md` §flow 系统态。

