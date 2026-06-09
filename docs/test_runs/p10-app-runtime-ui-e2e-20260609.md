# P10 应用模块与运行台浏览器 E2E

执行时间：2026-06-09

## 环境

| 项目 | 值 |
| --- | --- |
| 后端 | `http://127.0.0.1:9999` |
| 前端预览 | `http://127.0.0.1:4173` |
| 账号 | `platform_admin` |
| 系统 | `全链路系统pm70932135` |
| 系统 ID | `2064167820598476801` |

## 结果

| 步骤 | 结果 |
| --- | --- |
| 登录并进入真实系统 | pass |
| 创建应用 `P10应用071144` | pass |
| 创建模块 `P10模块071258` | pass |
| 创建字段 `标题071355` | pass |
| 保存列表/表单/详情配置 | pass |
| 保存菜单和记录动作 | pass |
| 发布检查 | pass |
| 发布模块 | pass，版本 `v1` |
| 深链路进入运行模块 | pass，自动 SYS-001 |
| 查询运行记录 | pass |
| 创建运行记录 | pass |
| 记录详情 | pass |
| 编辑保存 | pass |
| 提交 | pass，状态 `SUBMITTED` |
| 历史 | pass，包含 CREATE/UPDATE/SUBMIT |

## 发现并修复

| 问题 | 修复 |
| --- | --- |
| 发布模块使用旧版本号导致 `MODULE_CONFIG_VERSION_CONFLICT` | 发布前重新取模块详情，用最新版本发布 |
| 后端 schema 返回 `code/name`，前端按 `fieldCode/fieldName` 读取导致 RUN-004 参数不合法 | runtime schema 规范化兼容字段 |
| 深链路刷新缺少系统上下文 | 系统内路由自动执行 SYS-001 |
| RUN-003 把 POST 查询参数放 query | 改为 body |
| RUN-006/RUN-008 缺少幂等 key | 补齐 `X-Idempotency-Key` |
| 未配置标题字段时列表显示“未命名记录” | 后端使用第一个有文本的动态字段值兜底 |

截图：`frontend/docs/p10-app-runtime-e2e-final.png`

结论：TEST-008 通过。P10 页面不是接口调试页，已完成真实浏览器写操作链路。
