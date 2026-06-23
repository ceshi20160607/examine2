# 外部参考规约（网络沉淀 · 设计阶段通用）

> Agent 展开 **企业后台 / 无代码 / 多租户 / API 平台** 时必读。  
> 来源：WorkOS、Kong、AWS APIM、setting.page、GitNexa、STOA ADR-055 等（2024–2026）。

---

## 1. 多租户 SaaS（WorkOS / Yaro Labs / setting.page）

| 规约 | 做法 |
|------|------|
| 租户是一等维度 | 顶栏 **TenantSwitcher**；所有列表/API 带 tenantId |
| 设置分 scope | User → Workspace → Org → **Tenant**；写清继承与锁定 |
| 平台超管 vs 租户管理员 | 超管跨租户；租户管理员仅本租户 |
| 禁止 | 跨租户查询漏 filter；设置页 scope 不明 |

## 2. Portal / Console 分离（Kong / Apigee / STOA ADR-055）

| 界面 | 谁用 | 做什么 |
|------|------|--------|
| **Console**（管理控制台） | 提供方、平台管理员 | 创建/发布/治理 API、Flow、凭证、路由 |
| **Portal**（开发者门户） | 消费方、外部系统 | 发现 API、订阅、文档、调用示例 |

**规则：** 创建/管理资源 → Console；发现/使用资源 → Portal。禁止混在一个侧栏。

## 3. API 对外集成（Kong Manager / API7 / AWS APIM）

Console 必含：

- 服务/路由/插件（或等价 **API 产品**）
- Consumer + 凭证（Key/OAuth/mTLS）
- Scope / 授权范围
- **参数映射**（外部字段 ↔ 内部模块字段）
- 调用日志、限流、IP 白名单
- 发布工作流：草稿 → 发布 → 版本

## 4. 企业后台 UX（GitNexa）

1. **先工作流，后线框**
2. RBAC 驱动 UI（角色看不到的不要渲染）
3. **渐进披露**（复杂配置分步/抽屉）
4. 数据高密度 + 性能预算（虚拟滚动、服务端分页）
5. 多步流程要可恢复、可审计

## 5. 无代码平台参照（产品心智，非抄 UI）

| 参照 | 借鉴点 |
|------|--------|
| Microsoft Power Platform | 环境/解决方案 + 应用 + 数据模型 |
| Salesforce Platform | 对象/字段/页面/权限/Flow 分层 |
| Retool | 资源连接 + 应用构建 + 权限 |
| OutSystems | 模块、Lifetime、Service Center 运维 |

**本平台映射：** 平台层 = Service Center；自定义系统 = 应用环境；模块/字段/页面 = 数据模型+UI。

## 6. SSO（企业默认）

- 平台：**OIDC/SAML** IdP 配置页（MVP 原型须可见）
- 系统级：可选「继承平台 SSO / 独立登录页」
- 登录页：**密码登录 + SSO 按钮** 并存（Design 期）

## 7. 并入本仓库 Skill 的方式

| 外部规约 | 写入位置 |
|----------|----------|
| Portal/Console | L2 design-package 双入口 |
| TenantSwitcher | ui-spec 顶栏 |
| API 映射表 | config-spec external-app / api-console |
| 设置 scope | domain-glossary + admin-basic |

---

## 更新记录

| 日期 | 来源 | 摘要 |
|------|------|------|
| 2026-06-23 | 用户 nocode 测试 + web search | 初版 |
