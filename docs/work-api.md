# 工作协作 API 契约

管理接口请求发送 JSON，并携带登录后服务端会话 Cookie。`{tenantId}` 必须是当前成员实际加入的租户。列表默认 `page=1&size=20`，返回 `records,total,page,size`；`size` 最大 100，排序只接受各接口允许的字段。

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
