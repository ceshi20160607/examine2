# P12 Clean Build Report

执行时间：2026-06-11

任务：VAL-008 P12 Clean Build

结论：pass。P12 前端 clean build 和后端 clean package 均通过；本轮未生成 `dist/unexamine-full-deploy-*.zip` 最终部署包。

## 前端验证

| 项目 | 结果 |
| --- | --- |
| 清理 | 已删除旧 `frontend/dist/`，未发现 `frontend/tsconfig.tsbuildinfo` |
| 命令 | `npm.cmd run build` |
| 脚本 | `tsc --noEmit && vite build` |
| 结果 | pass |
| 产物 | `frontend/dist/index.html`、`frontend/dist/assets/index-DQmshVJC.css`、`frontend/dist/assets/index-HECxZvby.js` |

前端产物清单：

| 文件 | 大小 |
| --- | ---: |
| `frontend/dist/index.html` | 445 B |
| `frontend/dist/assets/index-DQmshVJC.css` | 11,755 B |
| `frontend/dist/assets/index-HECxZvby.js` | 208,177 B |

## 后端验证

| 项目 | 结果 |
| --- | --- |
| Java | `D:\java\jdk\jdk21` |
| Maven | `D:\java\apache-maven-3.8.5\bin\mvn.cmd` |
| 命令 | `mvn.cmd -pl examine-web -am clean package -DskipTests` |
| 结果 | pass，8 个 Maven 模块 SUCCESS |
| 产物 | `backend/examine-web/target/unexamine.jar` |
| 大小 | 43,607,964 B |

Maven Reactor：

| 模块 | 结果 |
| --- | --- |
| examine | SUCCESS |
| examine-core | SUCCESS |
| examine-plat | SUCCESS |
| examine-upload | SUCCESS |
| examine-module | SUCCESS |
| examine-flow | SUCCESS |
| examine-app | SUCCESS |
| examine-web | SUCCESS |

## Validator 结论

VAL-008 通过。TEST-010 已先行通过真实浏览器复测；本轮仅执行 clean build/package 验证，不生成最终部署 zip。下一步进入 REV-008，REV-008 未通过前 `PKG-001` 继续阻塞。
