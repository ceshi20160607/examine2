# 项目理解（Phase 0 冻结摘要）

> **agentId:** pm  
> **taskId:** phase-0-understanding  
> **日期:** 2026-06-15

## 1. PRD 与需求一致性

| 结论 | 说明 |
|------|------|
| **一致** | 双层架构、可配置模块、权限、流程、OpenAPI 方向与 `user_requirement.md` 一致 |
| **有意收缩** | MVP 不覆盖需求全文 §14 增强；导出 IA 按用户决策收敛 |
| **引用冻结** | 账号/导航/导出/ generator 以 `knowledge` + `resolution RES-2026-06-15-001` 为准 |

## 2. analysis 不确定项 — PM 裁决

| ID | 裁决 | 状态 |
|----|------|------|
| U-01 Flow 三类 MVP 范围 | MVP 仅 **基础审批**（模块提交→审批）；事件触发/大流程 → 增强期 | **closed** |
| U-02 平台「应用」导航 | 展示用户可访问的 **系统内业务应用** 聚合入口，非对外应用 | **closed** |
| U-03 添加成员 | 系统后台搜索/选择**已有平台用户**并授权；创建平台用户仅在**平台后台** | **closed** |
| U-04 导入 MVP | **不做** Excel 导入；导出通过动作 `export`（可先同步简版） | **closed** |
| U-05 仪表盘 MVP | **固定卡片**首页，不做拖拽设计器 | **closed** |
| U-06 UI 组件库 | **Design 阶段冻结**；PRD 候选 Naive UI 或 Ant Design Vue，Open Design 出原型时二选一 | **open P2** → 不阻 prd |
| U-07 多租户 | 表字段预留；MVP 剧本不验多租户 | **closed** |

## 3. Issue 台账（Discovery）

| issueId | raisedBy | problem | impact | pmDecision | owner | status |
|---------|----------|---------|--------|------------|-------|--------|
| ISS-D01 | analyst | 需求导出中心 vs 冻结动作模型 | P0 | 取消独立导出中心，见 prd D-01 | uiux | **closed** |
| ISS-D02 | analyst | 全量需求 vs MVP | P1 | prd §不做事项 | planner | **closed** |
| ISS-U06 | analyst | UI 库未选 | P2 | Design 阶段随原型冻结 | uiux | **open** |

**无 open P0 issue** → 建议 Conductor 置 `gates.prd_frozen = true`。

## 4. 是否允许进入下一阶段

| 下一阶段 | 条件 | 结论 |
|----------|------|------|
| Phase 1 Design | `prd_frozen` | **允许**（待 Gate 更新） |
| Phase 2 Contract | `design_user_approved` | **禁止**（须你先签原型） |
| Build | api + design | **禁止** |

## 5. 需你知悉（非阻塞）

- **U-06 UI 库**：若对 Naive UI / Ant Design Vue 有偏好，可在 Open Design 前说一句，否则 uiux 在 ui-spec 中给推荐。
- **注册流程**：PRD 采用「注册=建系统」；若你要独立「仅注册平台账号」入口，请回复以便开 RES 条目。

## 6. 下一步

1. Conductor 更新 `state.json`：`prd_frozen=true`，`phase=design`  
2. uiux：基于 prd + domain-model 写 `docs/design/ui-spec.md`  
3. Open Design 出原型 → 你在 `user-approval.md` 签字  
