# VAL-003 契约同步检查

- taskId: VAL-003
- 标题: 契约同步检查
- 负责角色: validator
- 所属大任务/模块: 构建验证 / 契约同步
- 目标: 检查冻结 API 中的错误码、枚举、状态值和前端 SDK、契约映射一致。
- 输入文件: `docs/api.md`、`frontend/src/api/`、`frontend/docs/api-contract-map.md`
- 输出文件或输出目录: `docs/build/contract-sync-check.md`

## 详细工作内容

- 比对 API 错误码命名空间、状态枚举、字段类型枚举和前端 SDK。
- 检查页面到接口映射是否覆盖 MVP 页面。
- 标记后端/API 已新增契约但前端未同步的缺口，并写入 `docs/build/contract-sync-check.md`。

## 完成状态定义

- 默认状态: pending。
- 完成条件: 同步检查结果写入 `docs/build/contract-sync-check.md`，最终 `docs/build_report.md` 只由 VAL-004 汇总写入。

## 验收标准

- 不一致必须判定失败并给出 frontend/api/both 来源。
- `frontend/docs/api-contract-map.md` 必须存在。

## 测试/自检要求

- 执行契约同步检查脚本或等价人工核对。

## 依赖任务

- FE-012

## 可并行关系

- 可与 VAL-001、VAL-002 并行。

## 不允许事项

- 不修改 API 或前端源码。
- 不忽略错误码和枚举同步缺口。
