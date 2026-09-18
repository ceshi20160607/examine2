# 项目 AI 控制面

这里保存“如何把需求变成可用系统”的项目事实，不保存产品源码。唯一产品需求仍是 `../../../docs/user_requirement.md`。

目录职责：

- `requirements/`：需求源索引、全量互斥分析包、原子需求和用例。
- `architecture/`：产品层级、运行时元数据、权限、租户和代码边界。
- `planning/`：当前方案、执行控制、需求账本和准确断点；旧排期仅是已有记录。
- `agents/`：既有角色任务记录；当前执行以 Spark 方案和断点为准。
- `decisions/`：需求冲突、未决问题和用户/总控裁决记录。
- `status/`：既有周期状态；当前状态由工程工具读取 Spark 断点，不迁入旧完成比例。
- `evidence/`：保存实际测试、持久化读回、API、页面、权限、失败和工程验证证据。

项目根目录 `question.md` 由 PM 维护。主流工程问题由 PM 直接裁决并记录，只有产品边界、不可逆数据变化或仍无法判断的问题才升级给用户。

当前执行入口见 `planning/README.md`、`planning/spark-execution-plan.md`、`planning/spark-state.json` 和 `planning/execution-control.json`。旧周期与角色任务单保留为已有记录，不作为当前任务选择、执行指令或完成判定依据。运行 `python template_base/tools/tb.py execution-context --workspace workspace.json` 取得当前范围；真实验证后检查记录并立即接续。当前不声明产品完成百分比。
