# TASK-{{ID}}：{{NAME}}

## 1. Meta

- status_ref: `session/state.json#active.taskExecution[{{ID}}].status`
- delivery_cycle_ref:
- goal_or_slice:
- requirement_ids: []
- acceptance_case_ids: []
- input_artifact_hashes: []
- owner:
- verifier: must differ from owner
- risk_class: routine | standard | critical
- quality_stage: functional | hardening
- verification_level: task
- estimate_minutes: 10..120
- module_scope:
- write_scope: []
- resource_scope: []
- depends_on_artifacts: []
- execution_mode: parallel | serial
- serial_reason: required only when a ready disjoint task is not parallel
- allowed_changes: []
- forbidden_changes: []
- base_bindings: []
- new_semantics_allowed: false
- unresolved_decision_ids: []
- responsive_scope: deferred | in_scope
- package_scope: forbidden_for_task

## 2. 单一结果

- 要实现的功能/工程结果：
- 可见或可读回位置：
- 不在本任务处理：
- 完成后解除的依赖：

页面、API、数据表、生成代码或单一模块允许成为 task；它们只能声明 task 完成，不能单独声明周期、旅程、模块或阶段完成。任务不得新增 `acceptance_case_ids` 没有授权的导航、按钮、API、数据表、迁移或业务状态。

## 3. Ready

- [ ] 目标来自 committed 原子需求和 required acceptance case。
- [ ] 消费所需契约 artifact 已 accepted。
- [ ] `unresolved_decision_ids=[]` 且 `new_semantics_allowed=false`。
- [ ] `module_scope` 与 `write_scope` 精确，和并行任务无重叠。
- [ ] 估算不超过 120 分钟；超出时已继续拆分。
- [ ] 验收命令可以在当前环境运行。
- [ ] 已声明 Base 能力使用方式：`reuse | configure | extend | override | project_only`。
- [ ] 用户可见任务消费了已冻结的参考桌面 UI 基线；导航、页面头、列表表头/工具栏/行操作、详情标签页和动作层级与基线一致。完整响应式可按计划延期到 hardening。

## 4. 验收

| affected layer | required check | command/result | evidence |
|---|---|---|---|
| build/unit | affected 时必填 |  |  |
| API/data | affected 时必填 |  |  |
| permission/security | affected 时必填 |  |  |
| UI/reference viewport | affected 时必填 |  |  |
| migration/operations | affected 时必填 |  |  |

未受影响层写 `not_affected: <reason>`，不生成占位截图或重复重启证据。所有任务由不同于 owner 的 verifier 验收。短任务禁止打包、全量回归、重复重启或浏览器矩阵；真实跨层入口、结合测试、生产构建和滚动包在所属 4 小时周期末统一验证一次。页面模块完成时执行该页面的完整旅程；阶段和项目验收只执行对应层级合同。性能、百万/千万数据和长并发不属于核心交付任务，只能在项目全部功能完成后按项目明确需要单独立项。

## 5. 偏差

- 仅当 active time 超过 120 分钟时记录：原因、剩余估算、拆分/继续决定和并行调整。
- 墙钟 target 过期只告警，不改变功能完成事实，也不阻断无关模块。

## 6. 完成

- verdict: pass | fail | blocked
- acceptance_command:
- test_execution_verdict: pass | fail
- business_outcome: achieved | denied | recovered | not_applicable
- requirement_acceptance_impact: none | partial | closes_required_case
- result_summary:
- remaining_scope:
- completed_at:
