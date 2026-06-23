# FE-008 运行台页面与动态表单联调

## 范围

- 页面入口：`runtime.home`、`runtime.module.records`、`runtime.record.detail`。
- 页面模型：`frontend/src/pages/runtime/runtimeWorkbenchPageModel.ts`。
- API 范围：RUN-001 至 RUN-010，全部来自冻结契约 `docs/api.md` 和 `frontend/src/api/endpoints.ts`。

## API 映射

| API | 页面能力 | 上下文 | 权限/状态 |
| --- | --- | --- | --- |
| RUN-001 | 运行台菜单加载 | systemId、memberId | SYSTEM_MEMBER |
| RUN-002 | 模块运行态 schema 加载 | systemId、moduleId、memberId | MENU_VISIBLE |
| RUN-003 | 记录分页查询 | systemId、moduleId、筛选、排序、分页 | RECORD_VIEW |
| RUN-004 | 新增记录 | 动态表单 values、remark、幂等键 | RECORD_CREATE、字段可写 |
| RUN-005 | 记录详情 | recordId、数据范围 | RECORD_VIEW_DATA_SCOPE |
| RUN-006 | 更新记录 | recordVersion、动态表单 values | RECORD_EDIT_FIELD_WRITE |
| RUN-007 | 软删除记录 | recordId | RECORD_DELETE，锁定状态禁用 |
| RUN-008 | 提交审批 | recordVersion、reason | RECORD_SUBMIT，非草稿/退回/撤回禁用 |
| RUN-009 | 记录历史 | recordId | RECORD_HISTORY_VIEW |
| RUN-010 | 记录关联关系 | recordId | RECORD_VIEW |

## 状态与错误处理

- 页面模型统一输出 `RuntimeRequestState`，保留 `requestId`、`retryable`、`errorMessage` 和字段级错误。
- 前端本地先通过动态表单 schema 校验必填、类型和字段可写，失败时不发起 RUN-004/RUN-006。
- 后端错误通过 `ErrorStore.capture` 转换为页面错误；字段错误使用 `mapApiFieldErrors` 回填到动态表单。
- `IN_APPROVAL`、`APPROVED`、`ARCHIVED`、`DELETED` 状态禁用删除；非 `DRAFT`、`REJECTED`、`WITHDRAWN` 状态禁用提交。

## 自检结论

- 已对齐 RUN-001 至 RUN-010 的路径参数、请求体、响应体、权限点和 requestId 展示。
- 当前前端目录仍无 `package.json`/`tsconfig.json`，无法执行正式 build 或 typecheck；本任务按静态模型和契约证据完成。
