# P9 PM Decisions - Frontend

| issueId | stage | raisedBy | owner | problem | impact | pmDecision | actionRequired | round | status | verifier | closeCondition |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P9-FE-001 | development | frontend/test | frontend | 成员、部门、系统角色、字典仍是通用空态面板。 | P9 无法给用户真实使用。 | 接受。P9 必须交付真实浏览器 UI，不接受 PageModel-only 或只触发首个 GET。 | 前端补 `system.members`、`system.departments`、`system.roles`、`system.dict` 四个真实页面；所有请求走 typed SDK 和真实系统上下文。 | 1 | open | test | 浏览器 E2E 覆盖成员、部门、角色、字典主要写操作，且无通用空态占位。 |
| P9-BE-002 | development | backend | frontend | 字典删除前端未传 `version`，与后端/冻结 API 不一致。 | 字典删除不可用。 | 以冻结 API 的 version 删除为准，不改成无 body 删除。 | 前端删除字典类型/字典项前加载 usage 和当前实体 version，删除时传 `DictDeleteBO.version`，并按返回实体/缓存信息展示结果。 | 1 | open | test | 字典未引用项/类型删除成功，引用中删除被阻断并显示原因。 |
