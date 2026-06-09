# 本机全系统功能链路验证记录（2026-06-09）

## PM 结论

本次验证按“本机启动前后端后跑整个项目功能”的口径执行，不再只按前端页面冒烟判断。

结论：后端全系统核心功能链路通过，前端仍不能因此宣称所有业务页面都已完整可视化完成。当前可以作为后端/接口功能验收依据，后续前端还需要继续按模块补齐成员、部门、角色、字典、应用模块、运行台、流程、文件、OpenAPI、审计运维等完整业务 UI。

## 环境

| 项目 | 值 |
| --- | --- |
| 后端 | `http://127.0.0.1:9999` |
| 前端 | `http://127.0.0.1:5173` |
| 登录账号 | `platform_admin / 123123aa` |
| 验证种子 | `pm70932135` |
| systemId | `2064167820598476801` |
| tenantId | `2064167820640419841` |
| memberId | `2064167820703334401` |

## 功能链路结果

| 功能域 | 结果 | 说明 |
| --- | --- | --- |
| AUTH 登录/刷新/me/未登录负向 | pass | 登录、刷新 token、当前用户、错误 token 拒绝通过 |
| PLATFORM 系统/账号/角色/配置 CRUD | pass | 创建系统、账号、平台角色、角色编辑、账号授权、配置查询通过 |
| SYSTEM 进入系统/租户/成员上下文 | pass | 系统进入、租户创建、当前成员查询通过 |
| MEMBER/RBAC 部门/角色/权限/成员 | pass | 部门、系统角色、权限保存、成员邀请、有效权限查询通过 |
| DICT 字典类型/字典项 CRUD | pass | 字典类型创建/更新、字典项创建/更新、使用情况查询通过 |
| MODULE/RUNTIME 应用/模块/字段/页面/发布/记录 | pass | 应用、模块、字段、页面配置、发布、运行态 schema、记录增改查通过 |
| FLOW 模板/图/发布/任务查询 | pass | 流程模板、流程图、发布检查、发布、待办查询通过 |
| FILE 上传/列表/详情/预览/下载/删除 | pass | 文件上传、查询、预览、下载、删除通过 |
| OPENAPI 客户端/凭证/scope/IP/日志 | pass | 客户端创建/更新、凭证轮换、scope 数组保存、IP 白名单数组保存、日志查询通过 |
| AUDIT/OPS 审计与运维查询 | pass | 系统审计、平台审计、健康检查、版本查询通过；健康状态为 `WARN` |

## 本次修复

1. `UnifiedResponseBodyAdvice` 跳过 `ResponseEntity` 和 `Resource`，避免文件预览/下载被统一响应包装后发生类型转换异常。
2. 流程草稿图保存时补齐 `publishedBy/publishedAt`，满足当前表结构非空约束。
3. OpenAPI VO 中长整型 ID 序列化为字符串，避免前端/脚本因 JavaScript 数字精度丢失导致 404。
4. 页面 schema 首次保存时在 insert 前写入 `schemaJson/updatedBy/updatedAt`，修复 `schema_json` 非空字段导致的 500。

## 验证命令

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn.cmd -pl examine-web -am test
mvn.cmd -pl examine-web -am clean package -DskipTests
```

结果：

- `mvn.cmd -pl examine-web -am test` 通过，8 个 Maven 模块共 68 个测试通过。
- `mvn.cmd -pl examine-web -am clean package -DskipTests` 通过，生成 `backend/examine-web/target/unexamine.jar`。
- 本机后端新 jar 已启动在 `127.0.0.1:9999`。
- 本机前端 dev server 已监听 `127.0.0.1:5173`。

## PM 管理修正

之前将 P8 页面验收直接等同“完整系统可用”是不准确的。后续 PM 口径调整为：

- 后端/接口功能通过：可以作为本机功能链路验收。
- 前端完整系统可用：必须逐模块具备真实业务页面、可操作表单、列表、详情、状态反馈和浏览器流程验证。
- 每次阶段结论必须同时说明“后端功能状态”“前端页面状态”“部署包状态”“剩余模块”，不能只写一个总体 pass。
