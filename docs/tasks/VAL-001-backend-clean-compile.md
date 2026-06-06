# VAL-001 后端 clean compile

- taskId: VAL-001
- 标题: 后端 clean compile
- 负责角色: validator
- 所属大任务/模块: 构建验证
- 目标: 使用指定 JDK/Maven 对后端执行 clean compile。
- 输入文件: `backend/`、`docs/service_info.md`
- 输出文件或输出目录: `docs/build/backend-clean-compile.md`

## 详细工作内容

- 按 `docs/service_info.md` 和本机 JDK/Maven 环境执行后端 clean compile。
- 记录实际使用的 JDK、Maven 路径和版本。
- 摘要记录失败日志和环境问题，写入 `docs/build/backend-clean-compile.md`。

## 完成状态定义

- 默认状态: pending。
- 完成条件: 后端 clean compile 结果写入 `docs/build/backend-clean-compile.md`，最终 `docs/build_report.md` 只由 VAL-004 汇总写入。

## 验收标准

- 不接受增量 `Nothing to compile` 作为唯一依据。
- 环境失败需定位 JDK、Maven 或配置原因。

## 测试/自检要求

- 执行后端 clean compile 命令。

## 依赖任务

- BE-015

## 可并行关系

- 可与 VAL-002、VAL-003 并行。

## 不允许事项

- 不修改后端代码。
- 不跳过 clean。
