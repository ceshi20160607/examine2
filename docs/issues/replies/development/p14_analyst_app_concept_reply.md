# P14 应用概念复核回复

状态：`closed-for-uiux-input`

## 复核输入

- `docs/user_requirement.md`
- `docs/prd.md`
- `.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/controller/PlatformAppController.java`
- `.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/entity/po/ModuleApp.java`
- `.codex/oldexamine/examine-web/src/main/resources/db/migration/V5__module_ddl.sql`
- `.codex/oldexamine/examine-web/src/main/resources/db/migration/V9__app_ddl.sql`

## Analyst 结论

P14 概念问题成立。原始需求和旧项目里同时存在两类不同对象，但都被叫成“应用”，导致前端和验收容易混淆。

必须在产品、UI、路由、文案和测试中强制区分：

1. 系统内业务应用。
2. 平台级对外应用。
3. OpenAPI 客户端/凭证。

## 三类对象边界

| 对象 | 所在层级 | 面向用户 | 核心职责 | 页面归属 |
| --- | --- | --- | --- | --- |
| 系统内业务应用 | 自定义系统层 | 系统管理员、建模配置人员、普通业务用户 | 组织业务模块、字段、页面、菜单和运行台入口。 | 系统层“建模配置”和“业务运行”。 |
| 平台级对外应用 | 平台层/集成开放层 | 平台管理员、集成管理员 | 管理外部系统接入主体，包含 appKey/secret、授权范围、安全策略和调用日志。 | 平台层“对外应用”。 |
| OpenAPI 客户端/凭证 | 技术接入对象 | 集成管理员、开发人员 | 对外应用下的访问凭证、签名、scope、限流、IP、幂等、日志。 | 对外应用详情页内的“凭证/授权/调用日志”。 |

## 旧项目证据

旧项目 `PlatformAppController` 使用 `/v1/platform/apps`，接口语义包含：

- 创建对外应用并生成 accessKey/secret。
- secret 仅本次返回。
- 查询对外应用列表和详情时不返回 secret。
- 更新对外应用资料。
- 启用、停用、删除对外应用。
- 轮换 secret。

这说明平台级对外应用是外部接入主体，不是系统内用来建模块的业务应用。

旧项目 `ModuleApp`、`V5__module_ddl.sql` 和模块域 controller 则表达的是系统内无代码应用，职责是承载模块、页面、菜单、成员、角色和发布版本。

旧项目 `V9__app_ddl.sql`、OpenAPI 安全过滤和凭证实体表达的是 OpenAPI 客户端/凭证域，职责是 AK/SK、签名、scope、限流、幂等和调用日志。

## UI/UX 处理要求

1. 页面上不得单独使用“应用”作为一级概念，必须写成“业务应用”或“对外应用”。
2. “业务应用”只能出现在业务系统内的建模配置、运行台组织和发布链路里。
3. “对外应用”必须作为平台层一级入口，用来管理外部接入。
4. OpenAPI 作为对外应用的技术能力，不应继续作为普通用户看到的系统内业务菜单。
5. 普通业务用户不应看到对外应用、密钥、scope、签名、限流和 IP 白名单。
6. 集成管理员应能在对外应用中完成创建、授权、密钥轮换、调用日志和错误追踪。

## 对 P14 的关闭条件

P14-APP-001 可关闭。其结论已写入：

- `docs/ui/p14-integrated-ui.md`
- `docs/tasks/P14-integrated-rework-plan.md`

后续 frontend、test、reviewer 必须按本回复验证术语和页面归属。
