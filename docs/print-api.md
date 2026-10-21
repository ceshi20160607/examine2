# 打印 API 契约

管理接口请求发送 JSON，并携带登录后服务端会话 Cookie。`{tenantId}` 必须是当前成员实际加入的租户。列表默认 `page=1&size=20`，返回 `records,total,page,size`；`size` 最大 100，排序只接受各接口允许的字段。渲染前会重新校验当前成员对记录的读取权限。

## 打印模板与渲染

| 路径前缀 | 方法和后缀 | 请求要点 | 结果 |
| --- | --- | --- | --- |
| `/api/tenants/{tenantId}/prints` | `GET/POST /`、`GET/PUT /{templateId}`、`GET /{templateId}/render?recordId=...` | 创建：`moduleId,templateCode,templateName,content`；更新：`templateName,content,enabled` | 仅对当前成员可读的模块记录替换字段生成纯文本，不执行模板脚本 |
