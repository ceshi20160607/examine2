# examine2 工程实例

本目录是 `.base` 3.1.0 在 examine2 项目中的实例。它只以 `docs/user_requirement.md` 为业务事实源，通过可执行合同把需求编译为项目阶段、原子需求、验收用例、任务 DAG 和最多四小时的交付周期。

## 当前事实入口

- Base 公共框架蓝图：`.cursor/FRAMEWORK.md`
- Examine2 当前项目重整方案：`.cursor/PROJECT_FRAMEWORK.md`
- 项目交付合同：`.cursor/session/project-delivery.json`
- 运行状态：`.cursor/session/state.json`
- 当前节点说明：`.cursor/session/current-node.md`
- 项目进度：`.cursor/session/project-progress.json` 与 `.cursor/session/PROJECT_PROGRESS.md`
- 路线图：`.cursor/session/delivery-roadmap.md`

## 四个强制 Skill

- `compile-project-delivery`：需求源 → 阶段 → 原子需求 → 用例 → 小任务 → 四小时周期。
- `build-management-ui`：模块列表、工具栏、表头、右侧详情标签页和可视化仪表盘合同。
- `implement-backend-module`：Base 复用、SQL/Repository 边界、复杂度止损和受影响单测。
- `verify-by-delivery-level`：task、cycle、module、phase、project 分层验证，避免重复全量测试。

## 当前策略

旧版 94% 进度、FAST 任务链和历史验收文件被视为历史归档材料，不进入默认上下文，也不再定义当前完成度。现有代码只按新合同选择性复用，其他旧产品面冻结。核心工程流程不包含性能、容量、百万数据或长并发测试。
