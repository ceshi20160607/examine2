# VAL-001 后端 clean compile

- taskId: VAL-001
- 标题: 后端 clean compile
- 负责角色: validator
- 所属大任务/模块: 验证 / 后端构建
- 目标: 使用 JDK 21 和 Maven 从 clean 状态编译后端聚合模块，验证不是增量编译假通过。
- 输入文件: `backend/`、`docs/service_info.md`、`docs/task_plan.md`
- 输出文件或输出目录: `docs/build/backend-clean-compile.md`

## 详细工作内容

- 临时设置 `JAVA_HOME=D:\java\jdk\jdk21`。
- 在 `backend/` 下执行 `mvn -pl examine-web -am clean compile`。
- 记录 Maven reactor 模块、命令、环境、结果和失败日志摘要。

## 完成状态定义

- 当前状态: done。
- 完成条件: `docs/build/backend-clean-compile.md` 已记录 clean compile 结果。

## 验收标准

- clean compile 必须覆盖 `examine-web` 及其依赖模块。
- 若失败，记录失败模块、关键日志和 target。

## 执行记录

- 2026-06-08 已执行 `mvn -pl examine-web -am clean compile`，8 个 Maven 模块均 SUCCESS。
- 记录见 `docs/build/backend-clean-compile.md`。
