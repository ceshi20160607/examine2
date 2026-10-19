# 工作、数据、应用与打印接口

这里记录当前后端已经实现的接口，供后端验收和之后的前端开发使用。管理接口请求发送 JSON 并携带登录后服务端会话 Cookie；应用凭据验证接口无需会话 Cookie。`{tenantId}` 必须是当前成员实际加入的租户。列表默认 `page=1&size=20`，返回 `records,total,page,size`；`size` 最大 100，排序只接受各接口允许的字段。

## 工作管理

| 路径前缀 | 方法和后缀 | 请求要点 | 结果 |
| --- | --- | --- | --- |
| `/api/tenants/{tenantId}/work/projects` | `GET /`、`POST /`、`GET /{projectId}`、`PUT /{projectId}` | 创建：`projectCode,projectName,description,ownerMemberId?`；更新：`projectName,description,status` | 项目列表、详情、创建和归档；仅负责人或租户管理员可管理 |
| 同上 | `GET/POST /{projectId}/members`、`DELETE /{projectId}/members/{relationId}` | 添加成员：`memberId`，必须先属于租户 | 项目成员可查看项目；负责人不可移除 |
| 同上 | `GET/POST /{projectId}/groups`、`PUT /{projectId}/groups/{groupId}` | `groupName,sortNo?` | 任务组配置 |
| `/api/tenants/{tenantId}/work/projects/{projectId}` | `GET/POST /fields`、`PUT /fields/{fieldId}` | 创建字段：`fieldKey,fieldName,fieldType,required,options?,sortNo?` | 支持 text、number、date、select、boolean；字段编码创建后固定 |
| 同上 | `GET/POST /tasks`、`GET/PUT /tasks/{taskId}` | 创建：`groupId,title,description?,ownerMemberId?,priority?,dueTime?,fieldData?`；修改另传 `version` | 列表可按 `createTime,dueTime,priority,title` 排序；修改用版本冲突保护 |
| 同上 | `PUT /tasks/{taskId}/status`、`GET /tasks/{taskId}/history` | `status,version`；状态为 todo、doing、done、cancelled | 状态改变记录历史 |
| 同上 | `GET/POST /tasks/{taskId}/members`、`DELETE /tasks/{taskId}/members/{relationId}` | `memberId,relationType`；类型为 collaborator、watcher、reviewer | 成员必须先属于项目；协作者可编辑任务 |
| `/api/tenants/{tenantId}/work/dailies` | `GET/POST /fields`、`PUT /fields/{fieldId}` | 字段结构同任务字段 | 日报字段配置 |
| 同上 | `GET/POST /`、`GET/PUT /{dailyId}`、`POST /{dailyId}/submit` | `workDate,content,fieldData?` | 日报列表、填写、修改、提交；提交后不可再编辑 |
| 同上 | `GET/POST /{dailyId}/comments` | `content` | 日报评论 |

## 数据展示与打印

| 路径前缀 | 方法和后缀 | 请求要点 | 结果 |
| --- | --- | --- | --- |
| `/api/tenants/{tenantId}/data-sources` | `GET/POST /`、`GET/PUT /{sourceId}`、`GET /{sourceId}/preview` | 创建：`sourceCode,sourceName,moduleId`；更新：`sourceName,enabled` | 数据源连接本租户启用的模块；预览返回当前成员可见记录数 |
| `/api/tenants/{tenantId}/dashboards` | `GET/POST /`、`GET/PUT /{dashboardId}` | 创建：`dashboardName,description?`；更新另传 `enabled` | 仪表盘配置及查看范围 |
| 同上 | `GET/POST /{dashboardId}/components`、`PUT /{dashboardId}/components/{componentId}` | `componentType,title,dataSourceId,configData?,layoutData?,sortNo?` | 组件及布局配置 |
| 同上 | `GET/POST /{dashboardId}/components/{componentId}/fields`、`GET /{dashboardId}/components/{componentId}/data` | 字段：`fieldKey,fieldLabel,aggregation,sortNo?` | 当前支持 count/sum；数据按成员可读记录范围计算 |
| 同上 | `GET/POST /{dashboardId}/users` | `memberId,permission`，权限 view/edit | 仪表盘共享 |
| `/api/tenants/{tenantId}/kpis` | `GET/POST /`、`GET/PUT /{kpiId}`、`PUT /{kpiId}/status` | 创建：`kpiCode,kpiName,dataSourceId,metricField?,aggregation,cycleType,targetValue?` | 仅支持全周期 all 的 count/sum，暂不接受自定义过滤 |
| 同上 | `GET /{kpiId}/members`、`PUT/DELETE /{kpiId}/members/{memberId}` | 目标：`targetValue` | 管理员分配个人目标；成员仅见自己被分配的 KPI |
| 同上 | `GET /{kpiId}/value`、`GET /{kpiId}/members/{memberId}/value` | 无请求体 | 汇总按可读记录范围；个人值按记录负责人计算 |
| `/api/tenants/{tenantId}/prints` | `GET/POST /`、`GET/PUT /{templateId}`、`GET /{templateId}/render?recordId=...` | 创建：`moduleId,templateCode,templateName,content`；更新：`templateName,content,enabled` | 仅对当前成员可读的模块记录替换字段生成纯文本，不执行模板脚本 |

## 应用配置

`/api/apps` 的 `POST /` 创建应用，请求 `systemId,tenantId?,appKey,appName`；系统范围应用需要平台管理员，租户范围应用需要所属租户管理员。响应中的 `secret` 只显示一次，数据库只存 BCrypt 摘要。`GET /?systemId=...&tenantId=...` 返回无密钥的分页列表，`PUT /{appId}` 修改名称或启停。

`POST /api/apps/verify` 无需登录，请求 `appKey,secret`；凭据正确且应用、所属系统及所属租户均启用时，返回 `id,systemId,tenantId,appKey,appName`，仅用于确认应用自身身份和范围。错误密钥、未知应用及停用范围统一返回 401。此接口不签发令牌，也不开放业务数据接口。`POST /api/apps/{appId}/secret/rotate` 需要所属范围管理员，响应中一次性返回新 `secret` 和无摘要的应用信息；旧密钥随数据库更新立即失效。

`POST/GET /api/apps/{appId}/events` 配置和查询事件订阅，`PUT /api/apps/{appId}/events/{eventId}` 修改订阅；事件请求为 `eventKey,callbackUrl`，回调地址必须是带合法 host 的 HTTPS URI。当前只保存事件配置，不向外部回调投递。
