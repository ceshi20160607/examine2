# 数据展示与 KPI API 契约

管理接口请求发送 JSON，并携带登录后服务端会话 Cookie。`{tenantId}` 必须是当前成员实际加入的租户。列表默认 `page=1&size=20`，返回 `records,total,page,size`；`size` 最大 100，排序只接受各接口允许的字段。数据源、仪表盘、KPI 只统计成员有权读取的模块记录。

## 数据源与仪表盘

| 路径前缀 | 方法和后缀 | 请求要点 | 结果 |
| --- | --- | --- | --- |
| `/api/tenants/{tenantId}/data-sources` | `GET/POST /`、`GET/PUT /{sourceId}`、`GET /{sourceId}/preview` | 创建：`sourceCode,sourceName,moduleId`；更新：`sourceName,enabled` | 数据源连接本租户启用的模块；预览返回当前成员可见记录数 |
| `/api/tenants/{tenantId}/dashboards` | `GET/POST /`、`GET/PUT /{dashboardId}` | 创建：`dashboardName,description?`；更新另传 `enabled` | 仪表盘配置及查看范围 |
| 同上 | `GET/POST /{dashboardId}/components`、`PUT /{dashboardId}/components/{componentId}` | `componentType,title,dataSourceId,configData?,layoutData?,sortNo?` | 组件及布局配置 |
| 同上 | `GET/POST /{dashboardId}/components/{componentId}/fields`、`GET /{dashboardId}/components/{componentId}/data` | 字段：`fieldKey,fieldLabel,aggregation,sortNo?` | 当前支持 count/sum；数据按成员可读记录范围计算 |
| 同上 | `GET/POST /{dashboardId}/users` | `memberId,permission`，权限 view/edit | 仪表盘共享 |

## KPI

路径前缀：`/api/tenants/{tenantId}/kpis`。创建时 `metricField` 和 `targetValue` 可选；`sum` 时 `metricField` 必填。管理员可维护本租户全部 KPI；普通成员只可读取分配给自己且已启用的 KPI。

| 方法与路径 | 请求关键字段 | 返回及用途 |
| --- | --- | --- |
| `GET /` | `page=1&size=20&sortField=createTime&sortOrder=desc`；排序字段 `createTime/kpiName/kpiCode` | `{ records, total, page, size }` |
| `POST /` | `{ "kpiCode": "sales_count", "kpiName": "数量", "dataSourceId": 1, "aggregation": "count", "cycleType": "all", "targetValue": 5 }` | KPI 定义，初始启用 |
| `GET /{kpiId}` | 无 | KPI 定义详情 |
| `PUT /{kpiId}` | `{ "kpiName": "...", "dataSourceId": 1, "metricField": "amount", "aggregation": "sum", "cycleType": "all", "targetValue": 10 }` | 更新定义；编码不可改 |
| `PUT /{kpiId}/status` | `{ "enabled": false }` | 启停 KPI |
| `GET /{kpiId}/members` | 无 | 管理员看到全部目标；普通成员只看到自己的 `UnKpiUser[]` |
| `PUT /{kpiId}/members/{memberId}` | `{ "targetValue": 7 }` | 新建或覆盖成员目标；`memberId` 是同租户系统成员主键 |
| `DELETE /{kpiId}/members/{memberId}` | 无 | 取消目标并撤销该成员读取范围 |
| `GET /{kpiId}/value` | 无 | `{ kpiId, targetValue, actualValue, cycleType }`，总实际值 |
| `GET /{kpiId}/members/{memberId}/value` | 无 | `{ kpiId, memberId, targetValue, actualValue, cycleType }`，按模块记录负责人计算个人值 |

只支持 `cycleType=all`、`aggregation=count|sum`。`sum` 必须提供可求和的 `metricField`；`count` 会使用 `*`。`filterData` 目前不可非空，其他周期直接返回 400，避免返回虚假统计。目标值须非负，最多四位小数。统计读取已配置数据源所指向的模块记录。
