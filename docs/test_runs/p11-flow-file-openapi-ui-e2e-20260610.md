# P11 流程、文件导出、OpenAPI、审计运维 UI E2E

执行时间：2026-06-10 16:10-16:42

执行角色：test / validator / reviewer 协同复验

## 环境

| 项目 | 值 |
| --- | --- |
| 后端 | `http://127.0.0.1:18080` |
| 前端 | `http://127.0.0.1:5174` |
| 登录账号 | `platform_admin / 123123aa` |
| 测试系统 | `2064332057713344514` |
| 租户 | `2064332057721733122` |
| 成员 | `2064332057725927425` |

## 覆盖结果

| 领域 | 结果 | 证据 |
| --- | --- | --- |
| 流程模板 | pass | 创建模板、保存默认图、发布、绑定模块均返回 `COMMON_OK` |
| 流程工作台 | pass | `FLOW-007/FLOW-013/FLOW-014/FLOW-017` 页面加载成功 |
| 文件中心 | pass | 文件上传成功，列表可回显文件记录 |
| 导出 | pass | 模块发布后创建导出任务，任务状态 `SUCCESS`，结果文件 ID 已返回 |
| OpenAPI | pass | 客户端创建成功，页面可加载 scope 目录与客户端列表 |
| 系统审计 | pass | `AUD-001` 页面加载成功 |
| 平台审计 | pass | `AUD-006` 页面加载成功 |
| 运维 | pass | `OPS-001/OPS-002/OPS-003/OPS-004/OPS-006` 页面加载成功 |

## 本轮发现并修复的问题

| 问题 | 修复 |
| --- | --- |
| 前端跨系统保留旧 app/module 状态，导致导出页选中旧模块 | 进入系统后按 systemId 重置 app/module/field 运行态，并在刷新 app/module 后校验选中项是否属于当前列表 |
| 导出页在 DRAFT 模块上允许创建导出 | 导出页改为只允许已发布模块创建导出模板和任务，并提示先发布模块 |
| 导出页刷新不更新模块发布状态 | `ensureModulesLoaded(true)` 支持强制刷新，导出页每次加载强制刷新模块状态 |
| 后端导出任务未传 filters 时写入 null，触发 `filter_snapshot_json` 非空约束 | 创建任务时将 `selectedRecordIds`、`filters`、`sorter` 默认保存为 `[]` |
| 流程发布检查前端读取 `errors/warnings`，后端返回 `issues` | 前端契约与提示改为读取 `issues` |
| 文件上传使用 JSON `Content-Type` 发送 FormData | transport 识别 FormData 并交由浏览器设置 multipart header |

## 结论

P11 通过。流程、文件导出、OpenAPI、审计运维页面已从占位/契约模型升级为可浏览、可写入、可回显的真实业务 UI，且后端导出任务链路已完成修复和复验。
