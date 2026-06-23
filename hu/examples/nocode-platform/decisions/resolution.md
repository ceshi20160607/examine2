# 决策（测试 AUTO）

## AUTO-001 演示垂直

- 演示自定义系统：**人事系统**
- 模块组：考勤管理；模块：请假申请
- 角色：platform_admin、hr_admin、employee

## AUTO-002 租户模式

- 人事系统启用 **多租户**（Design 须展示租户切换）
- 平台层管理租户配额与隔离策略

## AUTO-003 SSO MVP

- Design 须含：平台 IdP 配置页 + 登录页 SSO 按钮
- Build MVP：OIDC 优先；SAML P1 占位

## AUTO-004 Console / Portal

- P0：**API 控制台**（管理员配接口）
- P1：开发者 Portal（文档+订阅占位）

## AUTO-005 外部参照

- 平台 IA：Power Platform 环境切换 + Kong Console
- 列表/权限：Salesforce 对象管理 + 本仓库 lessons

## AUTO-006 与参照基线关系

- IA 与 [reference-case](../../feedforward/reference-case.md) **同族**（平台/系统/运行态三层）
- 增量：**平台租户管理、SSO、API 控制台、系统租户模式设置**
