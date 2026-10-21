# 应用接入 API 契约

管理接口发送 JSON，并携带平台或系统登录后的服务端会话 Cookie；`POST /api/apps/verify` 使用应用凭据，无需用户会话。列表默认 `page=1&size=20`，返回 `records,total,page,size`；`size` 最大 100。应用密钥只在创建或轮换时返回一次。

## 应用配置

`/api/apps` 的 `POST /` 创建应用，请求 `systemId,tenantId?,appKey,appName`；系统范围应用需要平台管理员，租户范围应用需要所属租户管理员。响应中的 `secret` 只显示一次，数据库只存 BCrypt 摘要。`GET /?systemId=...&tenantId=...` 返回无密钥的分页列表，`PUT /{appId}` 修改名称或启停。

`POST /api/apps/verify` 无需登录，请求 `appKey,secret`；凭据正确且应用、所属系统及所属租户均启用时，返回 `id,systemId,tenantId,appKey,appName`，仅用于确认应用自身身份和范围。错误密钥、未知应用及停用范围统一返回 401。此接口不签发令牌，也不开放业务数据接口。`POST /api/apps/{appId}/secret/rotate` 需要所属范围管理员，响应中一次性返回新 `secret` 和无摘要的应用信息；旧密钥随数据库更新立即失效。

`POST/GET /api/apps/{appId}/events` 配置和查询事件订阅，`PUT /api/apps/{appId}/events/{eventId}` 修改订阅；事件请求为 `eventKey,callbackUrl`，回调地址必须是带合法 host 的 HTTPS URI。当前只保存事件配置，不向外部回调投递。
