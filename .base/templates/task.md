# TASK-{{ID}}：{{NAME}}

## 1. Meta

- status_ref: `session/state.json#active.taskExecution[{{ID}}].status`
- delivery_cycle_ref:
- goal_or_slice:
- owner:
- verifier: required for standard/critical; optional for routine
- risk_class: routine | standard | critical
- quality_stage: functional | hardening
- estimate_minutes: 10..120
- module_scope:
- write_scope: []
- depends_on_artifacts: []
- execution_mode: parallel | serial
- serial_reason: required only when a ready disjoint task is not parallel
- responsive_scope: deferred | in_scope
- package_scope: forbidden_for_task

## 2. 单一结果

- 要实现的功能/工程结果：
- 可见或可读回位置：
- 不在本任务处理：
- 完成后解除的依赖：

页面、API、数据表、生成代码或单一模块允许成为 task；它们只能声明 task 完成，不能单独声明 slice、旅程或阶段完成。

## 3. Ready

- [ ] 目标来自未完成需求或当前工程使能缺口。
- [ ] 消费所需契约 artifact 已 accepted。
- [ ] `module_scope` 与 `write_scope` 精确，和并行任务无重叠。
- [ ] 估算不超过 120 分钟；超出时已继续拆分。
- [ ] 验收命令可以在当前环境运行。
- [ ] 用户可见任务消费了已冻结的参考桌面 UI 基线；导航、页面头、列表表头/工具栏/行操作、详情标签页和动作层级与基线一致。完整响应式可按计划延期到 hardening。

## 4. 验收

| affected layer | required check | command/result | evidence |
|---|---|---|---|
| build/unit | affected 时必填 |  |  |
| API/data | affected 时必填 |  |  |
| permission/security | affected 时必填 |  |  |
| UI/reference viewport | affected 时必填 |  |  |
| migration/operations | affected 时必填 |  |  |

未受影响层写 `not_affected: <reason>`，不生成占位截图或重复重启证据。routine task 由 owner 自检；standard/critical task 由独立 verifier 验收。短任务禁止打包、全量回归、重复重启或浏览器矩阵；真实跨层入口、累积回归、生产构建和滚动包在所属 4 小时周期末统一验证一次；完整响应式、无障碍、性能和安全矩阵在 phase/release hardening 验证。

## 5. 偏差

- 仅当 active time 超过 120 分钟时记录：原因、剩余估算、拆分/继续决定和并行调整。
- 墙钟 target 过期只告警，不改变功能完成事实，也不阻断无关模块。

## 6. 完成

- verdict: pass | fail | blocked
- acceptance_command:
- result_summary:
- remaining_scope:
- completed_at:
