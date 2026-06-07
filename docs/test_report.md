# P6 测试报告

- 任务: TEST-005
- 执行时间: 2026-06-08
- 汇总输入: `docs/test_plan.md`、`docs/test_runs/e2e-main-chain.md`、`docs/test_runs/permission-exception-idempotency-openapi.md`
- 结论: fail
- target: frontend

## 结论说明

后端 API 集成 smoke 已通过，可以继续进入 P6 的构建验证和审查任务；但完整上线验收不能标记为 pass，因为当前前端目录缺少 `package.json`、`tsconfig.json` 和可执行构建入口，无法执行正式浏览器 E2E、页面刷新、按钮禁用态和页面错误态验证。

## 执行命令

| 命令 | 结果 |
| --- | --- |
| `mvn -pl examine-web -am test` | 通过；core 13、plat 12、upload 4、module 21、flow 2、app 4、web 4 个测试通过 |
| `mvn -pl examine-web -am -DskipTests package` | 通过；生成 `backend/examine-web/target/unexamine.jar` |
| 启动 `unexamine.jar --server.port=9999` 后执行 backend API 主链路 HTTP 调用 | 通过；记录见 `docs/test_runs/e2e-main-chain.md` |
| 启动 `unexamine.jar --server.port=9999` 后执行权限、异常、OpenAPI 缺少 AK、幂等冲突 HTTP 调用 | 通过；记录见 `docs/test_runs/permission-exception-idempotency-openapi.md` |
| `git diff --check` | 通过；仅有 Git 工作区 LF/CRLF 转换 warning |

## TEST-003 汇总

| 场景 | 结果 |
| --- | --- |
| 注册、登录、创建系统、进入系统上下文 | 通过 |
| 创建应用、创建模块、创建字段、设置标题字段、保存菜单 | 通过 |
| 发布检查、发布版本、查询运行态 schema | 通过 |
| 创建记录、提交记录、创建导出任务 | 通过 |
| 前端浏览器刷新、页面联动、按钮禁用态 | 未执行；target=frontend |

## TEST-004 汇总

| 场景 | 结果 |
| --- | --- |
| 未登录访问内部 API | 通过；返回 401 `COMMON_UNAUTHORIZED` |
| 登录凭证错误 | 通过；返回 401 `AUTH_INVALID_CREDENTIAL` |
| OpenAPI 缺少 AK | 通过；返回 401 `COMMON_UNAUTHORIZED` |
| 创建系统同幂等键不同请求体 | 通过；返回 409 `COMMON_IDEMPOTENCY_CONFLICT` |
| OpenAPI timestamp/nonce/body hash 全矩阵、限流边界和并发压测 | 未完整执行；本期只完成 smoke 验证 |

## 失败与修复摘要

| 问题 | target | 状态 |
| --- | --- | --- |
| 系统创建人缺少系统内管理通配权限，主链路被权限拒绝 | backend | 已修复，`SYS_MANAGE_ALL` 通配权限单元测试通过 |
| 缺少 `Authorization` 请求头返回 500 | backend | 已修复，缺少认证头返回 401 |
| OpenAPI 调用日志缺少默认 `http_status` 导致落库失败 | backend | 已修复，创建日志默认写入 500 后再覆盖 |
| 菜单保存先插入再补必填字段导致数据库失败 | backend | 已修复，先组装必填字段再保存 |
| 导出日志缺少必填 `id` | backend | 已修复，写入 `IdWorker.getId()` |
| 创建系统同幂等键不同请求体未冲突 | backend | 已修复，返回 409 `COMMON_IDEMPOTENCY_CONFLICT` |
| 前端正式浏览器 E2E 无法执行 | frontend | 未修复，缺少前端工程入口 |

## 未覆盖风险

| 风险 | 影响 | 建议处理 |
| --- | --- | --- |
| 前端无构建入口，页面 E2E、刷新、禁用态、错误态无法验证 | 上线前无法确认真实用户界面是否可用 | VAL-002 必须判定失败并给出 frontend target，或补齐前端工程化入口后重跑 |
| OpenAPI 缺少 AK 当前返回通用 `COMMON_UNAUTHORIZED` | 若契约要求全部 OpenAPI 安全失败使用 `OPENAPI_*` 专属错误码，需调整契约或实现 | 由 PM/API 在后续回环确认是否细化 |
| OpenAPI timestamp、nonce、body hash、scope、IP、限流未做全矩阵自动化 | 安全边界只完成 smoke，不足以上线高安全要求 | 后续补充 API 自动化或压测脚本 |
| 并发场景未覆盖自动编号、流程任务并发处理、导出任务并发领取 | 高并发下仍可能出现状态冲突或重复处理漏洞 | 作为上线前专项或增强测试补齐 |

## 继续建议

1. 继续执行 VAL-001 后端 clean build，确认当前后端源码在 clean 环境可编译。
2. 执行 VAL-002 前端 clean build，预计因前端工程入口缺失失败，并将 target 指向 frontend。
3. 根据 VAL-002 结论决定是否先补前端工程入口，还是将当前 P6 标为 blocked/rework 等待前端工程化。
