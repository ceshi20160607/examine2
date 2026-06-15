# Skill: task-accept

## 触发

实现者声明 `TASK-XXX` done 之后；执行者 ≠ 实现者。

## 输入

- `docs/tasks/TASK-XXX.md`
- 实现者产出的 `outputs` 路径
- `registry.jsonl`（查相关 open issues）

## 输出

- `docs/evidence/accept-TASK-XXX.md`
- `docs/evidence/accept-TASK-XXX.log`

## 检查清单

见 `architecture/acceptance.md`

## verdict

- pass → Conductor 更新 `state.json.tasks[TASK-XXX]=accepted`
- fail → 写 fail 原因，开 issue 或 reopen
