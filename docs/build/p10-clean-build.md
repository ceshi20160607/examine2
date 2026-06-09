# P10 Clean Build

执行时间：2026-06-09

## 验证项

| 命令 | 结果 |
| --- | --- |
| `npm.cmd ci && npm.cmd run build` | pass |
| `mvn.cmd -pl examine-module -am -DskipTests compile` | pass |
| `mvn.cmd -pl examine-web -am -DskipTests clean package` | pass |

## 产物

| 产物 | 路径 |
| --- | --- |
| 前端 dist | `frontend/dist/` |
| 后端 jar | `backend/examine-web/target/unexamine.jar` |
| P10 部署包目录 | `dist/unexamine-full-deploy-20260609-162432/` |
| P10 部署包 zip | `dist/unexamine-full-deploy-20260609-162432.zip` |

## 说明

`npm.cmd ci` 仍提示 2 个 moderate audit 项，按既有策略记录为依赖治理风险，未执行可能破坏依赖版本的 `npm audit fix --force`。
