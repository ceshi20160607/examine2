# 当前执行入口

本轮从 `spark-execution-plan.md` 和 `spark-state.json` 接手；`spark-requirement-ledger.json` 逐条索引唯一需求源 `docs/user_requirement.md`，不另立产品范围。

执行顺序由 B00—B19 及其子步骤确定，不再根据旧周期编号续写。先读取状态中的 `next_step`，只展开当前工作包的需求和源码。

工程入口由根 `workspace.json.activeProject.executionControl` 绑定到 `execution-control.json`，复用 Template Base 工具，不另建产品计划：

```powershell
python template_base/tools/tb.py execution-context --workspace workspace.json
python template_base/tools/tb.py execution-verify --workspace workspace.json --receipt <当前步骤验证记录>
python template_base/tools/tb.py project-status --workspace workspace.json
```

`execution-context` 校验需求绑定、当前断点与范围一致、显式前置步骤证据及入口路径，只输出当前原文和验收，正文上限 16,000 字符。源码按入口和直接依赖读取。后续 `current_scope` 都须写 `prerequisite_steps`，即使为空。需求改变时先对齐受影响原文和证据，不能只改哈希绕过失败。

实施后先保存真实验证记录，再调用 `execution-verify` 检查当前范围、读回产物、测试文件哈希和下一子步骤。该命令只检查记录完整性，不能证明真实业务结果。通过后更新状态，并立即重新调用工程入口；当前状态不引用旧周期完成比例。

`execution-control.json.policy` 是工程接续规则，`wakeUp` 是本项目的唤醒配置，宿主定时仅同步配置并启动工程入口。工具本身不会调用模型，也不会在无人运行时自动开发。宿主可替换，任务和完成判定始终留在项目内。外部阻塞按既有方案保留并继续独立工作；不得因一个步骤完成等待下一条“继续”。

原 `schedule-plan.json`、`task-catalog.json`、`task-graph.json`、`task-layering.json` 和旧复审/顺序文档保留为已有工作记录，不作为本轮任务选择或完成判定依据；它们与本轮方案冲突时，不执行旧指令。旧“完成”状态不能迁入新账本。

方案交付仅证明已经完成规划。全部需求初始为未验证，必须依据当前实现和真实业务结果逐项验证；已有可用代码优先复用。

---

以下为原计划说明，停止作为执行指令。

## 原计划生成顺序

1. 合并全量需求分析，建立每个产品能力的唯一归属。
2. 为每个能力拆原子用例和验收条件。
3. 每个用例再拆成数据库、生成 `base`、后端 `manage`、前端、测试与运维任务；不需要的任务类型明确标记不适用。
4. 审计现有代码，逐项判定保留、补全、重做或删除；已有代码不能改变任务范围。
5. 建立依赖和资源冲突后，才按真实预计时间把任务装入最多 240 分钟的周期；不设置人为最小时长，单项超过 240 分钟必须继续拆分。

交付阶段只表示依赖里程碑，与 Template Base 的 9 个质量关口分离。一个能力只能有一个主阶段；成熟化、安全和性能是该能力的约束或验收任务，不能再次把整项能力排一遍。

当前结果为 206 个任务、6 个依赖阶段、64 个周期，总估算 13,845 分钟。历史 56 个功能与工程周期保留证据；第 57 至 64 周期依据 `ux-hardening-baseline.md` 重新完成产品体验 Hardening。周期内只有包含 `integration-test` 任务时才声明真实页面旅程；数据库、Base 等基础周期以空库迁移、生成一致性和编译结果验收，不伪造尚未可执行的页面结果，也不再用功能可点击代替页面清晰度和使用意愿验收。
