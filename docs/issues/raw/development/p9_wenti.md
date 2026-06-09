# P9 Development Raw Issues

| issueId | stage | raisedBy | owner | problem | impact | suggestion | blockerLevel |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P9-GOV-001 | development | planner | planner | `development-phases.md` 和 `docs/tasks/` 尚未正式登记 P9，但后续工作已经指向成员、部门、系统角色、字典 UI。 | 没有边界就实现会再次变成临场推进，无法验收。 | 新增 P9 期次和 P9 任务文件，明确范围、输出、验证和不可宣称事项。 | blocker |
| P9-FE-001 | development | frontend/test | frontend | `system.members`、`system.departments`、`system.roles`、`system.dict` 路由已存在，但 `App.ts` 仍落到通用空态查询面板。 | 用户看不到可操作系统管理页，不能算完整业务 UI。 | P9 必须补真实 UI，覆盖列表、表单、状态、授权、使用情况和删除限制。 | blocker |
| P9-BE-001 | development | backend | backend | `MEM-001` 后端返回 `List<MemberVO>`，前端 PageModel 按分页结果消费；筛选字段也不完全一致。 | 成员列表页面按当前模型直接对接会失败或展示不完整。 | 后端补齐分页和 `deptId/roleId` 筛选，或 PM 允许前端临时适配数组返回。 | major |
| P9-BE-002 | development | backend | backend/frontend | `DICT-010/011` 后端删除需要 `DictDeleteBO.version`，前端 PageModel 当前未传 body；删除返回结构也不一致。 | 字典删除 UI 会直接不可用或误判删除成功。 | PM 裁决以冻结 API 的 version 删除为准，前端补传 version 并按后端返回实体处理。 | major |
| P9-PM-001 | development | frontend | pm/reviewer | `docs/review.json.fullProjectDeployable=true` 与 `docs/progress.md`、治理文档中“完整系统仍需 P9-P12”冲突。 | 用户会误以为整个系统已经可上线。 | PM 撤回完整系统可部署结论，保留 P8 局部可试部署结论。 | blocker |
