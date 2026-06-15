# TASK-{ID}

## meta

- task_id: TASK-XXX
- type: implementation | contract-only | design | test
- owner: backend | frontend | dba | uiux | test
- phase: build
- parallel_group: G1 | none
- depends_on: []

## goal

（一句话目标）

## inputs

- path/to/input.md

## outputs

- path/to/output（必须不与其他并行任务重叠）

## scope

### 做

-

### 不做

-

## self_check_commands

```powershell
# 命令
```

## acceptance

- [ ] 所有 outputs 存在且非空
- [ ] self_check_commands 通过，日志在 docs/evidence/accept-TASK-XXX.log
- [ ] skill task-accept verdict=pass
- [ ] 无新增 P0 issue

## integration_test

（交给 test 的入口说明）
