# 应用访问接入说明

应用是系统间以及外部访问业务资源的受控入口。目标系统管理员发布应用授权后，调用仍受目标系统、租户、资源动作、字段、负责人数据范围和应用状态约束。不要把普通系统登录令牌当作外部应用凭据。

## 配置目标系统应用

在目标系统后台建立应用，选择目标租户，逐项发布需要的模块 LIST、DETAIL、Flow START 等动作及允许返回的字段和数据范围。先用只读动作完成接入，再单独评估写入授权。变更授权要发布新版本；停用应用后验证调用被拒绝并检查调用日志。

平台内部的系统间调用使用 `POST /api/application-internal-access/v1/applications/{applicationId}/calls`，由来源系统中获权成员的 Bearer 令牌调用，带 `X-Idempotency-Key`。请求体包含 `targetSystemId`、`targetTenantId`、`resourceType`、`resourceId`、`actionCode`、`requestedDataScope` 和 `payload`。来源成员不能借此扩大目标授权范围，也不能直接进入目标系统。

## 外部签名调用

外部调用使用 `POST /api/application-access/v1/calls`。从应用凭据管理取得 clientId 和签名密钥后，仅在接入方的密钥管理系统保存密钥。请求头为 `X-App-Client-Id`、毫秒时间戳 `X-App-Timestamp`、随机 `X-App-Nonce`、`X-App-Idempotency-Key` 和十六进制 `X-App-Signature`。时间戳与服务端时间相差超过五分钟会被拒绝，同一 nonce 不可重放。

签名原文由 clientId、时间戳、nonce、幂等键、原始 JSON 请求体的 SHA-256 小写十六进制摘要按此顺序以换行连接；用 UTF-8 密钥计算 HMAC-SHA256，再以小写十六进制放入签名头。请求体字节必须与摘要所用字节完全一致。请求体字段与内部调用相同；字段授权和数据范围按目标应用已发布版本校验。

调用失败时记录响应码、请求标识和调用时间，在目标应用调用日志核对。`APPLICATION_GRANT_DENIED` 表示资源动作未授权，`APPLICATION_DISABLED` 表示应用停用，签名或时间戳错误应先检查原始请求体字节、时钟和凭据版本。不要在工单中附签名密钥。
