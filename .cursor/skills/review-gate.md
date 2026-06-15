# Skill: review-gate

## 触发

每个 phase 结束前。

## 输入

- `session/state.json`
- `registry.jsonl`
- `docs/evidence/*`

## 输出

`docs/evidence/gate-{phase}.json`

## 检查

对应 phase 的 gates 条件（见 `architecture/gates.md`）

## 规则

`verdict=fail` → Conductor 不得推进 phase
