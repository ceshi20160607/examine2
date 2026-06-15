# Phase 3: Build

## 入口

- `design_user_approved = true`
- `api_frozen = true`
- `tasks_planned = true`

## 步骤

1. planner 产出 `docs/tasks/TASK-*.md`
2. Conductor 按 DAG 调度；无依赖任务 **并行** 新会话
3. dba 如需 → `sql/init.sql`
4. backend / frontend 按 TASK 实现
5. 每 TASK 完成 → skill **task-accept**（实现者 ≠ 验收）
6. 批次末 → skill **clean-build**

## 并行示例

```
并行组 G1: TASK-DBA-001, TASK-BE-003（路径不重叠）
串行: TASK-BE-004 depends_on BE-003
并行组 G2: TASK-FE-005（depends 满足后）
```

## 问题

任何 Worker 可写 registry → pm triage → 继续

## 退出

- [ ] 当前里程碑 TASK 全部 `accepted`
- [ ] clean-build pass
- [ ] 无 open P0 build issues

## 下一步

→ `phase-4-verify.md`
