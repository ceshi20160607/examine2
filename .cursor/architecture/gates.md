# 冻结闸门（Gates）

状态字段在 `.cursor/session/state.json` 的 `gates` 对象。

## 闸门定义

| Gate | 谁签字 | 条件 | 阻塞 |
|------|--------|------|------|
| `prd_frozen` | pm | PRD 含 MVP；无 open P0 discovery issues | Design |
| `design_package_complete` | **conductor**（依据 reviews/* 全 pass） | `design-package.md` 无 TBD；`prototype-brief` 与页面表 1:1 | **建议用户调用 Open Design** |
| `design_user_approved` | **仅你** | `user-approval.md` `approved: true` | Contract、Build、一切代码 |
| `api_frozen` | pm | `docs/api/api.md` 版本号；多角色复核 closed；contract-sync pass | Build |
| `tasks_planned` | pm | `docs/tasks/plan.md` 含依赖图与并行组 | Build 实现 |
| `build_batch_accepted` | skill task-accept | 当前批次任务均有 accept 证据 | Verify |
| `user_script_passed` | test + 你 | e2e 车系统剧本证据；你试用无 P0 反馈 | 打包 |

## 最关键：两道设计门

### 门 1：设计包内部完工（Open Design 之前，**团队**完成）

```json
"gates": { "design_package_complete": true }
```

见 [workflows/phase-1-internal-design-complete.md](../workflows/phase-1-internal-design-complete.md)。  
多角色审阅 `docs/design/reviews/*`，PM 闭环 issue，**不要求用户填 IA 表**。

### 门 2：原型签字（开发之前，**你**）

```json
"gates": {
  "design_user_approved": false
}
```

当 `false` 时 Conductor **必须拒绝**：

- 创建/修改 `backend/**`
- 创建/修改 `frontend/**`
- 创建/修改 `sql/**`
- 进入 contract / build workflow
- planner 创建实现类任务

**允许：**

- discovery 文档
- `docs/design/**` 设计与原型
- Open Design 迭代直到你满意

## 你的签字文件格式

`docs/design/user-approval.md`：

```markdown
# 设计确认

- approved: true | false
- approved_at: 2026-06-15
- approved_by: user
- scope: 车系统 MVP 全平台+系统+运行台
- prototypes_reviewed:
  - docs/design/prototypes/platform-home.html
  - docs/design/prototypes/system-runtime.html
- notes: （修改意见或确认说明）
```

仅当 `approved: true` 时，Conductor 可将 `gates.design_user_approved` 设为 `true`。

## Gate 变更记录

每次 Gate 变化写入 `session/events/*.jsonl`：

```json
{"ts":"...","actor":"conductor","action":"gate_pass","ref":"design_user_approved","detail":"user-approval.md approved:true"}
```
