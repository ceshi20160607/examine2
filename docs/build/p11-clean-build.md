# P11 Clean Build

执行时间：2026-06-10

| 项目 | 结果 |
| --- | --- |
| 前端构建 | `D:\java\nodejs\npm.cmd run build` pass，生成 `frontend/dist/` |
| 后端编译 | `mvn -pl examine-web -am -DskipTests compile` pass |
| 后端打包 | `mvn -pl examine-web -am -DskipTests package` pass，生成 `backend/examine-web/target/unexamine.jar` |
| 浏览器 E2E | P11 路由 smoke 和关键写链路通过 |

## 产物

| 产物 | 路径 |
| --- | --- |
| 前端 dist | `frontend/dist/` |
| 后端 jar | `backend/examine-web/target/unexamine.jar` |
| 测试记录 | `docs/test_runs/p11-flow-file-openapi-ui-e2e-20260610.md` |

## Validator 结论

P11 clean build pass。前端构建、后端编译、后端打包和 P11 浏览器链路均通过。
