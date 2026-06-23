# PRD — FlexBase 无代码平台（MVP · 测试展开）

> 从 3 段粗糙需求展开 · RPFD L1

## 1. 产品一句话

**FlexBase** 是可配置业务系统平台：平台层治理多系统、SSO 与对外 API；自定义系统层无代码搭建模块、组织、权限与流程；运行态给业务用户使用。

## 2. 用户

| 角色 | 目标 |
|------|------|
| 平台管理员 | 管理系统、平台 SSO、平台 Flow/API 产品、平台用户 |
| 系统管理员 | 配置系统租户模式、模块、组织、权限、系统 Flow/对外应用 |
| 业务用户 | 使用运行态业务、处理待办 |

## 3. MVP 必须

- 平台层 + 自定义系统层 + 运行态 **三层 IA**
- 系统 **单租户/多租户** 可配置（系统设置页）
- **SSO**（平台 IdP + 登录页入口）
- 组织架构、成员、角色权限（分页）
- 模块组 → 模块 → 建模 → 发布
- 系统 Flow + **平台 Flow**（分入口）
- **API 控制台**：对外应用、scope、字段映射、调用日志
- 日志、待办、消息（顶栏）
- 演示系统：**人事·请假** 端到端

## 4. 不做（首版）

- 移动端原生 App
- 拖拽自由布局仪表盘
- 完整开发者 Portal 订阅计费
- SAML 实装（可占位）
- AI 生成模块

## 5. 演示剧本（15 步）

| 步 | 角色 | 动作 | 页面 ID |
|----|------|------|---------|
| 1 | platform_admin | SSO/密码登录 | P-LOGIN |
| 2 | platform_admin | 平台租户与用户 | P-PLAT-ADMIN-TENANTS |
| 3 | platform_admin | 配置平台 SSO | P-PLAT-ADMIN-SSO |
| 4 | platform_admin | 进入人事系统后台 | P-SYS-ADMIN-HOME |
| 5 | hr_admin | 系统租户模式=多租户 | P-SYS-ADMIN-TENANT |
| 6 | hr_admin | 组织架构 | P-SYS-ADMIN-ORG |
| 7 | hr_admin | 模块组+模块 | P-SYS-ADMIN-MODULE-GROUP / MODULE |
| 8 | hr_admin | 建模发布 | P-SYS-ADMIN-MODELING |
| 9 | hr_admin | 角色+添加 employee | P-SYS-ADMIN-ROLES / USERS |
| 10 | hr_admin | 系统 Flow | P-SYS-ADMIN-FLOW |
| 11 | platform_admin | 平台 API 产品+映射 | P-PLAT-ADMIN-API-CONSOLE |
| 12 | employee | 登录→仪表盘 | P-SYS-DASHBOARD |
| 13 | employee | 请假列表+新建 | P-RUNTIME-LIST / FORM |
| 14 | hr_admin | 待办审批 | P-SYS-FLOW-TODO |
| 15 | employee | 全程无系统后台入口 | — |

## 6. 参照产品

Power Platform、Salesforce Setup、Kong Manager、WorkOS（租户切换）
