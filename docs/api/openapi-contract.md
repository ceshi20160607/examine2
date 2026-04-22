## OpenAPI 契约（/v1/open/**）

本文件定义对外开放 API 的**鉴权、代操作、幂等与通用错误码**约定；与可运行的请求示例配合使用：
- `docs/api/curl-examples.md`

### 1) 路径与版本

- **前缀**：`/v1/open/**`
- **版本**：`v1` 作为路径的一段；兼容策略（弃用窗口、是否并行 v2）以后续发布说明为准

### 2) 鉴权（AK/SK）

每次请求必须携带：

- `X-Access-Key`: 应用的 AK
- `X-Secret`: 应用的 SK（当前实现为“请求头传 SK”，后续可升级为签名模式）

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

### 7) Flow OpenAPI（当前已提供的能力）

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

