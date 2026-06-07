# P6 构建验证报告

- 任务: VAL-004
- 执行时间: 2026-06-08
- 执行角色: validator
- 结论: fail
- target: frontend

## 汇总结论

后端 clean compile 通过，说明当前后端源码可以从干净状态编译。前端 clean build 失败，原因是 `frontend/package.json` 不存在；契约同步检查失败，原因是前端字段类型枚举未按冻结 API 同步。因此 P6 构建验证整体为 fail，target=frontend。

## 子任务结果

| 子任务 | 记录 | 结论 | target |
| --- | --- | --- | --- |
| VAL-001 后端 clean compile | `docs/build/backend-clean-compile.md` | pass | 无 |
| VAL-002 前端 clean build | `docs/build/frontend-clean-build.md` | fail | frontend |
| VAL-003 契约同步检查 | `docs/build/contract-sync-check.md` | fail | frontend |

## 后端验证

| 项目 | 结果 |
| --- | --- |
| 命令 | `mvn -pl examine-web -am clean compile` |
| JDK | `D:\java\jdk\jdk21` |
| Maven | `D:\java\apache-maven-3.8.5\bin` |
| Reactor | examine、examine-core、examine-plat、examine-upload、examine-module、examine-flow、examine-app、examine-web |
| 结论 | 8 个模块均 SUCCESS |

## 前端验证

| 项目 | 结果 |
| --- | --- |
| Node | `v24.14.0` |
| npm | `11.9.0` |
| 命令 | `npm.cmd run build` |
| 失败原因 | `frontend/package.json` 不存在，npm 返回 ENOENT |
| 源码产物扫描 | `frontend/src` 未发现 `.vue.js`、临时 `.d.ts` 或编译 `.js` |

## 契约同步

| 检查项 | 结果 |
| --- | --- |
| API ID | 174 个冻结 API 均同步到 `frontend/src/api/endpoints.ts` |
| `api-contract-map.md` API ID | 174 个冻结 API 均覆盖 |
| 核心错误码 | 20 个核心错误码均包含于 `frontend/src/api/errorCodes.ts` |
| 状态枚举 | 15 组状态枚举同步 |
| 字段类型枚举 | fail，前端字段类型与冻结 API 不一致 |

字段类型差异：

- API 有、前端缺失：`AUTO_NO`、`DEPT`、`MEMBER`、`MONEY`、`SWITCH`。
- 前端有、API 未冻结：`BOOLEAN`、`CHECKBOX`、`DECIMAL`、`DICT`、`RADIO`、`SERIAL`。

## 失败摘要

| 问题 | target | 影响 |
| --- | --- | --- |
| `frontend/package.json` 缺失 | frontend | 无法执行 clean build、typecheck、lint 和浏览器 E2E |
| `frontend/tsconfig.json` 缺失 | frontend | 无法做 TypeScript 类型校验 |
| 字段类型枚举未同步 | frontend | 动态字段配置、运行态表单和页面契约判断可能与冻结 API 不一致 |

## Validator 结论

P6 构建验证不通过，target=frontend。建议后续先补齐前端工程入口，并把 `frontend/src/api/enums.ts` 的字段类型枚举修正为冻结 API 枚举，之后重跑 VAL-002、VAL-003 和 VAL-004。
