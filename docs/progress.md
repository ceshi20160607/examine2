# 项目进度看板

更新时间：2026-06-07

## 总览

| 项目 | 数量 |
| --- | ---: |
| 开发执行任务总数 | 50 |
| 已完成 | 28 |
| 进行中 | 0 |
| 阻塞 | 0 |
| 剩余 | 22 |

当前模式：`development`

当前期次：`P3-system-config`

当前状态：`P3_system_config_in_progress`

## 分期进度

| 期次 | 名称 | 状态 | 完成情况 | 下一步 |
| --- | --- | --- | --- | --- |
| P0-foundation | 基础冻结与骨架期 | done | 19 个任务已完成 | 已作为后续开发基础 |
| P1-generator | 生成器闭环期 | accepted | 3/3 | 已通过 PM 阶段验收 |
| P2-auth-platform | 认证与平台期 | accepted | 2/2 后端主任务，FE-004 静态联调补充完成 | 已通过 PM 阶段验收 |
| P3-system-config | 系统配置与权限期 | in_progress | 4/4 后端主任务，前端待联调 | P3 前端静态联调补充与阶段验收 |
| P4-runtime-mvp | 运行台 MVP 期 | pending | 0/2 | 等 P3 通过 |
| P5-workflow-files-openapi | 流程文件导出 OpenAPI 期 | pending | 0/5 后端主任务，前端待联调 | 等 P4 通过 |
| P6-final-acceptance | 集成验收与上线判断期 | pending | 0/14 | 等 P5 通过 |

## 角色完成度

| 角色 | 已完成 | 进行中 | 待执行 | 说明 |
| --- | ---: | ---: | ---: | --- |
| DBA | 6 | 0 | 0 | DB 设计与 init.sql 已完成。 |
| Backend | 8 | 0 | 7 | BE-001 至 BE-007、BE-014 已完成；BE-008 至 BE-015 待按期推进。 |
| Generator | 4 | 0 | 0 | GEN-001 至 GEN-004 已完成，生成器闭环通过。 |
| Frontend | 8 | 0 | 4 | FE-001 至 FE-007、FE-011 已完成；FE-008 至 FE-010、FE-012 待执行。 |
| Test | 2 | 0 | 3 | TEST-001、TEST-002 已完成；最终场景测试待后续期。 |
| Validator | 0 | 0 | 4 | 等阶段或最终构建验证。 |
| Reviewer | 0 | 0 | 4 | 等测试和构建产物。 |

## 当前 Agent 状态

| agent | taskId | 状态 | 输出 | 备注 |
| --- | --- | --- | --- | --- |
| backend | GEN-002 | done | 生成器命令参数 | 已改为命令即配置，不再维护表映射文件。 |
| backend | GEN-003 | done | `backend/examine-generator/src/main/resources/templates/base/` | base 模板策略与自定义模板已落地。 |
| backend | GEN-004 | done | 各模块 `base/` 包 | 已执行 MyBatis-Plus 生成，后端 compile 通过。 |
| backend | BE-003 | done | `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/`、`backend/examine-web/src/main/java/com/unique/examine/ExamineWebApplication.java` | 认证会话接口和单测完成。 |
| backend | BE-004 | done | `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/` | 平台中心接口、系统初始化写入器、平台中心单测完成。 |
| backend | BE-014 | done | `backend/examine-core/`、`backend/examine-module/` | 统一权限服务、字段权限、数据范围与 OpenAPI scope 校验完成。 |
| backend | BE-005 | done | `backend/examine-module/src/main/java/com/unique/examine/module/manage/` | 系统上下文、租户、成员、部门、角色、授权目录和有效权限接口完成。 |
| backend | BE-006 | done | `backend/examine-module/src/main/java/com/unique/examine/module/manage/` | 字典类型、字典项、使用情况、引用限制和缓存版本刷新接口完成。 |
| backend | BE-007 | done | `backend/examine-module/src/main/java/com/unique/examine/module/manage/` | APP、MOD、FIELD、UI、发布检查和发布版本生成接口完成。 |

## 当前期任务

| taskId | 名称 | 状态 | 负责人 | 输出 |
| --- | --- | --- | --- | --- |
| GEN-002 | 生成器数据库映射配置 | done | backend | 命令参数传入模块、前缀、base 包和输出目录 |
| GEN-003 | base 层模板策略 | done | backend | 生成模板与规则 |
| GEN-004 | 生成执行 | done | backend | 各模块 `base/` 包 |
| BE-003 | 认证会话安全 | done | backend | 注册、登录、刷新、退出、当前用户接口 |
| BE-004 | 平台中心 API | done | backend | 平台账号、平台角色、系统创建、状态、配置和权限目录接口 |
| BE-014 | 权限与数据范围拦截 | done | backend | 统一权限服务、上下文、字段权限、数据范围规则和 OpenAPI scope 校验 |
| BE-005 | 系统成员 RBAC API | done | backend | SYS、MEM、RBAC 接口和权限版本刷新 |
| BE-006 | 字典管理 API | done | backend | DICT-001 至 DICT-011 接口和字典规则 |
| BE-007 | 应用模块字段页面配置 API | done | backend | APP、MOD、FIELD、UI 接口和发布检查 |

## 下一步

P2 已通过 PM 阶段验收，当前进入 `P3-system-config`。`BE-014` 权限与数据范围拦截、`BE-005` 系统成员 RBAC API、`BE-006` 字典管理 API、`BE-007` 应用模块字段页面配置 API 已完成，下一步进行 P3 前端静态联调补充与阶段验收。

P1 验证摘要：

1. `mvn -DskipTests compile` 后端完整编译通过。
2. `sql/init.sql` 已调整为 `examine1` 库，83 张表均为 `DROP TABLE IF EXISTS` 后 `CREATE TABLE`。
3. 已通过 JDBC 将 `sql/init.sql` 执行到 `192.168.0.211:3306/examine1`，执行 181 条 SQL，数据库中确认 83 张 `un_` 表。
4. `examine-generator` 已升级为命令驱动：每条命令显式传入模块名、表前缀、base 包和输出目录，不再维护映射文件或默认生成报告。
5. 83 个 base service 均由 `examine-generator` 自动生成 `queryById`、`queryAll`、`queryPage`、`addOrUpdate`、`deleteByIds` 基础 CRUD 方法。
6. 83 个 mapper XML 均生成 `BaseResultMap`，`base` 包未生成 Controller，旧包名和旧 Swagger2 注解命中为 0。

BE-003 验证摘要：

1. `mvn -pl examine-plat -am test` 通过，core 7 个测试、plat 6 个测试。
2. `mvn -DskipTests compile` 后端完整编译通过。
3. 覆盖密码错误、账号停用、连续失败锁定、刷新 token、退出幂等和 token 过期。

BE-004 验证摘要：

1. 已实现平台中心系统、账号、角色、配置、权限目录接口，所有平台中心接口统一校验 Bearer 登录态。
2. 系统创建在同一事务内写入平台系统、默认租户，并通过初始化写入器创建系统内成员、超级管理员角色、默认菜单、默认操作、默认应用和权限版本。
3. `mvn -pl examine-plat -am test` 通过，core 7 个测试、plat 12 个测试。
4. `mvn clean compile` 后端完整 clean 编译通过。

P2 验收摘要：

1. 验收记录已写入 `docs/phases/P2-auth-platform-acceptance.md`。
2. FE-004 已按 BE-004 当前响应修正平台页面字段映射，并补齐 PLAT-013 至 PLAT-020 的 Bearer 与权限点。
3. 前端目录当前没有 `package.json` 或可执行构建入口，本期只完成静态契约核对，最终前端 clean build 留到 P6 或前端工程补齐后执行。

BE-014 验证摘要：

1. 已在 `RequestContext` 中补齐 systemId、memberId、clientId 请求上下文字段，并通过过滤器读取 `X-System-Id`、`X-Member-Id`、`X-Client-Id`。
2. 已新增统一权限服务，支持菜单、操作、字段可写、数据范围和 OpenAPI scope 判定。
3. `examine-module` 已提供成员角色权限快照，汇总角色菜单、操作、字段权限、数据范围、OpenAPI scope 和权限版本。
4. `mvn -pl examine-core test` 通过，core 12 个测试。
5. `mvn -pl examine-module -am -DskipTests compile` 通过。
6. `mvn clean compile` 后端完整 clean 编译通过。

BE-005 验证摘要：

1. 已实现 SYS、MEM、RBAC 接口组，覆盖系统进入、资料、租户、成员、部门树、角色、角色授权、有效权限、运行菜单和权限目录。
2. 角色授权支持菜单、操作、字段权限、数据范围和 OpenAPI scope，保存后写入权限版本。
3. 已增加 `mock-maker-subclass` 测试配置，规避当前 JDK 下 Mockito inline 自 attach 不稳定问题。
4. `mvn -pl examine-module -am test` 通过，core 12 个测试、plat 12 个测试、module 3 个测试。
5. `mvn clean compile` 后端完整 clean 编译通过。

BE-006 验证摘要：

1. 已实现 DICT-001 至 DICT-011，覆盖字典类型列表、创建、更新、启停、删除、使用情况，以及字典项列表、创建、更新、启停、删除。
2. 字典服务已落地编码唯一、内置只读、引用限制、最大 5 级层级、父级停用限制、软删除和缓存版本刷新。
3. 新增 `DICT` 错误码命名空间和 `DICT_*` 精准错误码。
4. `mvn -pl examine-module -am test` 通过，core 12 个测试、plat 12 个测试、module 9 个测试。
5. `mvn clean compile` 后端完整 clean 编译通过。

BE-007 验证摘要：

1. 已实现 APP、MOD、FIELD、UI 接口组，覆盖应用、模块、字段、字段类型、默认列表、表单、详情、运行菜单、动作配置、发布检查和发布。
2. 发布检查已返回 requestId 和可定位 issue，覆盖字段类型、唯一约束、页面字段引用和运行菜单配置。
3. 发布接口检查通过后写入 `un_module_publish_version` 快照，并更新模块当前发布版本；发布检查接口不生成版本。
4. 新增 `MODULE_*`、`FIELD_*`、`MODULE_PAGE_FIELD_MISSING` 等精准错误码。
5. `mvn -pl examine-module -am test` 通过，core 12 个测试、plat 12 个测试、module 15 个测试。
6. `mvn clean compile` 后端完整 clean 编译通过。
