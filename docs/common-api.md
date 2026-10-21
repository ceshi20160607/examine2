# 公共资源 API 契约

基础地址为 `http://localhost:9999`。先通过平台或系统会话登录，后续请求携带服务端会话 Cookie；除文件上传使用 `multipart/form-data`，请求体使用 JSON。文件、公告、AI 配置按 `platform/system/tenant` 范围管理。列表 `page` 从 1 开始，`size` 为 1～100，默认 1 和 20；响应为 MyBatis-Plus 分页对象 `records,total,size,current,pages`。

未登录、越权、参数错误、资源不存在、编码或状态冲突分别返回 HTTP `401/403/400/404/409`。

## 公共资源范围

公共资源使用 `scopeType=platform|system|tenant` 和 `scopeId`。平台范围固定 `scopeId=0`；系统和租户范围使用其真实主键。资源读取需对应范围身份，平台管理员可以读取系统和租户资源。文件上传允许对应范围的有效成员；删除需要管理员。公告和 AI 配置写入：平台及系统范围由平台管理员负责，租户范围由租户管理员或平台管理员负责。

| 方法与路径 | 请求或查询 | 响应与说明 |
| --- | --- | --- |
| `GET /api/common/files` | `scopeType,scopeId,page,size,sort`；`sort=originalName,fileSize,createTime` | 分页文件元数据，不返回存储路径。 |
| `POST /api/common/files` | `multipart/form-data`：`scopeType,scopeId,file` | 上传实际文件并保存元数据；单文件上限 25 MB，响应含 `id,originalName,contentType,fileSize,createTime`。 |
| `GET /api/common/files/{id}/download` | 无 | 附件二进制；下载前重新校验文件所属范围。 |
| `DELETE /api/common/files/{id}` | 无 | 删除文件和元数据；范围管理员。 |
| `GET /api/common/notices` | `scopeType,scopeId,page,size,sort,includeDrafts`；`sort=title,createTime,publishTime` | 默认只返回当前有效发布公告；`includeDrafts=true` 需要管理员。 |
| `GET /api/common/notices/{id}` | 无 | 已发布且未过期公告对范围成员可见；草稿、撤销、过期公告仅管理员可见。 |
| `POST /api/common/notices` | `{scopeType,scopeId,title,content,expireTime}` | 新建草稿，`status=0`。 |
| `PUT /api/common/notices/{id}` | `{title,content,expireTime}` | 仅可编辑草稿。 |
| `POST /api/common/notices/{id}/publish` / `POST /api/common/notices/{id}/revoke` | 无 | 草稿→发布 `status=1` / 发布→撤销 `status=2`。 |
| `GET /api/common/ai-configs` | `scopeType,scopeId,page,size,sort`；`sort=configCode,providerName,createTime` | 管理员分页读取，只有 `credentialConfigured` 标志，不返回凭据或密文。 |
| `POST /api/common/ai-configs` | `{scopeType,scopeId,configCode,providerName,modelName,apiBaseUrl,credential}` | 新建配置；有凭据时以 AES-GCM 加密存储。 |
| `PUT /api/common/ai-configs/{id}` | `{configCode,providerName,modelName,apiBaseUrl,credential}` | 修改配置；省略 `credential` 则保留原凭据。 |
| `PUT /api/common/ai-configs/{id}/status` | `{status}` | 停启配置。 |

写入 AI 凭据前必须设置 32 字节密钥的 Base64 环境变量 `APP_AI_ENCRYPTION_KEY`；缺失或格式错误时返回 `503`，不会保存明文。当前没有 AI 服务连通性测试接口。
