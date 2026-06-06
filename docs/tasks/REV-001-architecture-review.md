# REV-001 架构审查

- taskId: REV-001
- 标题: 架构审查
- 负责角色: reviewer
- 所属大任务/模块: Review / 架构
- 目标: 审查后端模块边界、base/manage 分层、生成器、事务、权限和前端架构。
- 输入文件: `docs/prd.md`、`docs/api.md`、`docs/db_design.md`、`backend/`、`frontend/`、`docs/build_report.md`
- 输出文件或输出目录: `docs/review_parts/rev-001-architecture.md`

## 详细工作内容

- 检查 Maven 多模块职责和 `examine-web` 是否堆业务实现。
- 检查生成器是否只输出 base 层，业务是否放在 manage 层。
- 检查事务、异步、远程上传补偿、权限拦截和数据范围风险。

## 完成状态定义

- 默认状态: pending。
- 完成条件: 架构问题形成 `docs/review_parts/rev-001-architecture.md`，供 REV-002/REV-004 汇总。

## 验收标准

- 高风险架构问题必须指向具体文件或目录。
- 能判断 target 为 backend/frontend/both/api/dba。

## 测试/自检要求

- 对照 PRD 和 API 的实现约束逐项审查。

## 依赖任务

- VAL-004

## 可并行关系

- 不可并行；reviewer 审查按顺序执行。

## 不允许事项

- 不修改代码或文档。
- 不用泛泛意见替代可定位问题。
