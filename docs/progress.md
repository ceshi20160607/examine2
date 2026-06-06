# 项目进度看板

更新时间：2026-06-06

## 总览

| 项目 | 数量 |
| --- | ---: |
| 开发执行任务总数 | 50 |
| 已完成 | 23 |
| 进行中 | 0 |
| 阻塞 | 0 |
| 剩余 | 27 |

当前模式：`development`

当前期次：`P2-auth-platform`

当前状态：`P2_auth_platform_in_progress`

## 分期进度

| 期次 | 名称 | 状态 | 完成情况 | 下一步 |
| --- | --- | --- | --- | --- |
| P0-foundation | 基础冻结与骨架期 | done | 19 个任务已完成 | 已作为后续开发基础 |
| P1-generator | 生成器闭环期 | accepted | 3/3 | 已通过 PM 阶段验收 |
| P2-auth-platform | 认证与平台期 | in_progress | 1/2 后端主任务，前端待联调 | 执行 BE-004 |
| P3-system-config | 系统配置与权限期 | pending | 0/4 后端主任务，前端待联调 | 等 P2 通过 |
| P4-runtime-mvp | 运行台 MVP 期 | pending | 0/2 | 等 P3 通过 |
| P5-workflow-files-openapi | 流程文件导出 OpenAPI 期 | pending | 0/5 后端主任务，前端待联调 | 等 P4 通过 |
| P6-final-acceptance | 集成验收与上线判断期 | pending | 0/14 | 等 P5 通过 |

## 角色完成度

| 角色 | 已完成 | 进行中 | 待执行 | 说明 |
| --- | ---: | ---: | ---: | --- |
| DBA | 6 | 0 | 0 | DB 设计与 init.sql 已完成。 |
| Backend | 3 | 0 | 12 | BE-001 至 BE-003 已完成；BE-004 至 BE-015 待按期推进。 |
| Generator | 4 | 0 | 0 | GEN-001 至 GEN-004 已完成，生成器闭环通过。 |
| Frontend | 8 | 0 | 4 | FE-001 至 FE-007、FE-011 已完成；FE-008 至 FE-010、FE-012 待执行。 |
| Test | 2 | 0 | 3 | TEST-001、TEST-002 已完成；最终场景测试待后续期。 |
| Validator | 0 | 0 | 4 | 等阶段或最终构建验证。 |
| Reviewer | 0 | 0 | 4 | 等测试和构建产物。 |

## 当前 Agent 状态

| agent | taskId | 状态 | 输出 | 备注 |
| --- | --- | --- | --- | --- |
| backend | GEN-002 | done | `backend/examine-generator/src/main/resources/generator/table-module-map.yml` | 83 张表映射覆盖，旧路径/旧包名检查通过。 |
| backend | GEN-003 | done | `backend/examine-generator/src/main/resources/generator/base-template-strategy.yml` | base 模板策略与自定义模板已落地。 |
| backend | GEN-004 | done | `backend/docs/mybatis-plus-generation.md`、各模块 `base/` 包 | 已执行 MyBatis-Plus 生成，后端 compile 通过。 |
| backend | BE-003 | done | `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/`、`backend/examine-web/src/main/java/com/unique/examine/ExamineWebApplication.java` | 认证会话接口和单测完成。 |

## 当前期任务

| taskId | 名称 | 状态 | 负责人 | 输出 |
| --- | --- | --- | --- | --- |
| GEN-002 | 生成器数据库映射配置 | done | backend | `backend/examine-generator/src/main/resources/generator/table-module-map.yml` |
| GEN-003 | base 层模板策略 | done | backend | 生成模板与规则 |
| GEN-004 | 生成执行与报告 | done | backend | 各模块 `base/` 包、`backend/docs/mybatis-plus-generation.md` |
| BE-003 | 认证会话安全 | done | backend | 注册、登录、刷新、退出、当前用户接口 |

## 下一步

P1 已通过 PM 阶段验收，当前进入 `P2-auth-platform`。`BE-003` 认证会话安全已完成，下一步执行 `BE-004` 平台中心 API。

P1 验证摘要：

1. `mvn -DskipTests compile` 后端完整编译通过。
2. `sql/init.sql` 83 张表全部生成 entity，源码 mapper XML 数量 83。
3. 83 个 base service 均由 `examine-generator` 自动生成 `queryById`、`queryAll`、`queryPage`、`addOrUpdate`、`deleteByIds` 基础 CRUD 方法。
4. 83 个 mapper XML 均生成 `BaseResultMap`，`base` 包未生成 Controller，旧包名和旧 Swagger2 注解命中为 0。

BE-003 验证摘要：

1. `mvn -pl examine-plat -am test` 通过，core 7 个测试、plat 6 个测试。
2. `mvn -DskipTests compile` 后端完整编译通过。
3. 覆盖密码错误、账号停用、连续失败锁定、刷新 token、退出幂等和 token 过期。
