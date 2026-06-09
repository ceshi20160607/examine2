# P9 PM Decisions - Backend

| issueId | stage | raisedBy | owner | problem | impact | pmDecision | actionRequired | round | status | verifier | closeCondition |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P9-BE-001 | development | backend | backend | `MEM-001` 返回数组且筛选字段不足，与前端分页 PageModel 不一致。 | 成员列表 UI 会缺分页和部门/角色筛选能力。 | P9 先接受前端适配数组返回，后端不在本批次改冻结 API；原因是核心成员 CRUD 可用，分页/复杂筛选不阻塞 MVP 真实 UI。后续 P12 前若要完整上线，再单独评估契约一致性回补。 | 后端本批次无需改代码；若前端无法适配，则重新打开 issue。 | 1 | resolved | frontend | 前端成员页能展示成员列表并完成邀请、编辑、启停、分配角色。 |
