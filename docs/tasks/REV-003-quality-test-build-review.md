# REV-003 质量测试构建审查

- taskId: REV-003
- 标题: 质量测试构建审查
- 负责角色: reviewer
- 所属大任务/模块: Review / 质量
- 目标: 审查代码质量、测试覆盖、构建报告和未覆盖风险。
- 输入文件: `docs/test_report.md`、`docs/build_report.md`、`backend/`、`frontend/`
- 输出文件或输出目录: `docs/review_parts/rev-003-quality.md`

## 详细工作内容

- 检查测试是否覆盖主链路、权限、异常、幂等、OpenAPI、审计和构建失败风险。
- 检查后端 clean compile、前端 clean build、契约同步检查是否真实执行。
- 检查是否存在临时文件、旁路编译产物、未处理失败日志或未说明风险。

## 完成状态定义

- 默认状态: pending。
- 完成条件: 质量和验证问题形成 `docs/review_parts/rev-003-quality.md`，供 REV-004 汇总。

## 验收标准

- issues 必须能指向 `backend/`、`frontend/`、`docs/` 或 `sql/` 下的具体文件或目录。

## 测试/自检要求

- 对照 test 和 validator 报告核实命令、结果和失败摘要。

## 依赖任务

- REV-002

## 可并行关系

- 不可并行；依赖契约审查结论。

## 不允许事项

- 不因构建未执行而输出 pass。
- 不忽略剩余测试缺口。
