# OpenAPI 集成手册

系统应用和平台应用分别管理，作用域及调用日志互不混用。应用页面仅显示 app key 标识、`SecretRef`、凭证版本、状态、到期/轮换和最近使用，不返回 secret 明文。

## 请求签名

调用方使用当前凭证对规范请求签名，并发送 app key、credential version、UTC timestamp、唯一 nonce、body SHA-256 和 signature。服务端依次校验应用状态、scope、时间窗、nonce 防重放、IP allowlist、限流与签名；写操作还要求唯一幂等键。具体 header 名和 route scope 以包内运行时 OpenAPI 描述为准，不从日志复制 secret。

推荐调用流程：

1. 计算原始 body 的 SHA-256；GET 使用空 body 哈希。
2. 规范化 HTTP method、原始 path/query、timestamp、nonce、body hash。
3. 使用 HMAC-SHA256 和当前 secret 计算签名。
4. 发送请求并保存返回的 requestId/traceId；超时重试复用幂等键，但必须使用新 nonce/timestamp/signature。

## 错误处理

- 401：签名、时间窗、nonce、凭证版本或应用状态错误；不盲目重试。
- 403：scope/IP/对象权限不足；调整授权而不是扩大所有 scope。
- 409：幂等冲突或版本冲突；读取现状后决定。
- 429：超过应用限流；指数退避并遵守服务端提示。
- 5xx：保存 requestId/traceId，使用同一幂等键安全重试写操作。

轮换采用双版本过渡：启用新 SecretRef → 调用方切换并验证 → 撤销旧版本。调用日志记录结果类别、HTTP 状态、延迟、requestId/traceId 和来源 IP，不记录请求 secret 或敏感响应体。
