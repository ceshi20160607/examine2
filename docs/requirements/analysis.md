# 需求增量分析（Phase 0）

> **agentId:** analyst  
> **taskId:** phase-0-delta-analysis  
> **日期:** 2026-06-15

## 1. 与 knowledge 已一致（仅引用，不重讨论）

| 主题 | 引用 |
|------|------|
| 双层架构（平台/系统） | `frozen-rules §1.2`、`domain-model §1` |
| 统一认证、注册即建系统 | `frozen-rules §1.3`、`resolution RES-2026-06-15-001 §2` |
| 导出=页面动作权限 | `frozen-rules §1.4`、`resolution §1`；`user_requirement §5.14` 亦写「非独立中心」 |
| 平台 IA：我的系统/Flow/应用/待办/消息 | `domain-model §3`、`resolution §3` |
| 进系统默认仪表盘 | `domain-model §3.2`、`resolution §3` |
| Flow 三类 | `domain-model §4` |
| base/manage + generator | `backend-structure`、`resolution §4` |
| 种子账号 plat_admin | `frozen-rules §3.2` |
| 车系统/che 验收剧本 | `frozen-rules §1.10` |
| 技术分层、Gate 顺序 | `frozen-rules §三、§四` |

## 2. 需求 vs knowledge 需 PM 收敛的差异

| ID | 需求原文位置 | 差异说明 | 建议 |
|----|--------------|----------|------|
| D-01 | `user_requirement §4.3` 页面清单含 **「导出中心」** | 与用户决策「导出非模块/flow 同级」及 §5.14「模块内功能」并存，易做成独立菜单 | PRD 定为：**取消独立「导出中心」导航**；导出/导入配置并入 **模块功能/动作配置**；列表页动作区触发导出任务 |
| D-02 | `user_requirement §4.4` **应用运行台** 为主叙事 | knowledge/用户决策默认落点为 **系统仪表盘 + 权限内模块** | PRD 统一文案：**系统仪表盘**为选系统后默认首页；业务列表/表单/详情为模块功能入口（运行态） |
| D-03 | `user_requirement §4.1` 注册、创建系统分述 | 用户决策：**未登录注册 = 注册一个系统 + 超管** | PRD 明确一条 **「注册并创建首个业务系统」** 主流程；`plat_admin` 种子用于运维/演示 |
| D-04 | `user_requirement` 全文 **全量**（§14 KPI/打印/数据源/Agent 等） | v2 需 MVP 切片，否则重复 v1 过度交付 | PRD 必须写清 **MVP / 增强 / 不做**；首期锚点车系统剧本 |
| D-05 | `user_requirement §5.14` 导出权限「独立控制」 | 与动作权限模型一致，但「独立」易被误解为独立产品域 | PRD 表述为 **动作权限位 `export`**，非独立模块 |
| D-06 | 系统内 **OpenAPI 配置** vs 平台 **对外应用** | 需求两处均出现 OpenAPI 文案 | 术语强制：`系统内 OpenAPI 授权` vs `平台对外应用`（`frozen-rules §1.6`） |

## 3. 不确定项（须 PM 裁决或用户 escalated）

| ID | 问题 | 影响 | 建议责任方 |
|----|------|------|------------|
| U-01 | **Flow 三类**是否全部进入 MVP？还是首期仅「基础审批」？ | 平台导航、后端 flow 模块范围 | PM 提议 MVP 仅基础审批 + 绑定模块提交；事件触发/大流程 → 增强期 |
| U-02 | **平台「应用」导航**展示什么？跨系统应用列表 vs 快捷入口？ | 平台工作台 IA、Open Design 原型 | PM 草案：展示用户有权限的 **系统内业务应用** 聚合入口，非对外应用 |
| U-03 | **系统内添加成员**交互：仅关联已有平台用户，还是跳转平台开户？ | 车系统剧本步骤 4–5 | MVP：系统后台「添加成员」= 搜索平台用户 + 授权；平台管理员在平台后台「创建用户」 |
| U-04 | **导入**是否进 MVP？需求 §5.14 很重 | 车系统剧本未要求导入 | 建议 MVP **仅导出动作 + 简同步导出**；Excel 导入 → 增强期 |
| U-05 | **仪表盘** MVP 粒度：空统计+待办 vs 可配置组件？ | 前端工作量 | MVP：**系统首页固定卡片**（模块数、待办数、快捷入口）；可拖拽仪表盘 → 增强期 |
| U-06 | 前端组件库：需求列 Ant Design Vue / Element Plus / Naive UI | 技术选型影响 Design | PM 建议 **Vue 3 + Vite + 一套 UI**（Design 阶段冻结，暂定 Ant Design Vue 或 Naive UI 二选一） |
| U-07 | **多租户**是否在 MVP 启用？需求多处 tenantId | 表结构与权限复杂度 | 建议 MVP **单租户字段预留**、业务剧本不验多租户 |

## 4. 风险项

| ID | 风险 | 级别 |
|----|------|------|
| R-01 | 需求 2600+ 行，若 PRD 复述全文则再次文档膨胀 | P1 |
| R-02 | v1 后端能力多但 UI 失败；重建时易「后端先行」再次误判完成 | P0 流程风险（靠 Gate/E2E 缓解） |
| R-03 | `user_requirement §4.3` 导出中心与冻结规则冲突，UI 若照需求画图会做错 | P0 产品风险 → PRD 必须写死 D-01 裁决 |

## 5. 不建议讨论（已冻结）

- 平台/系统是否分层
- base 是否生成器生成
- 是否先 UI 再开发
- PM vs 用户决策边界
- 验收必须车系统 E2E

见 `failure-lessons.md`、`decision-authority.md`。

## 6. analyst 结论

- **可进入 PM 写 prd**：无阻碍理解的矛盾；D-01~D-06 可在 PRD 中一次性裁决。
- **建议 escalated 给用户**：仅当 PM 对 U-01（Flow 三类 MVP 范围）或 U-06（UI 库）无法拍板时。
- **无新增 frozen-rules 变更**，除非用户回复 U-01/U-04/U-07。
