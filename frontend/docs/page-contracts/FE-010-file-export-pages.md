# FE-010 文件与导出页面契约证据

## 范围

- 页面入口：`files.center`、`exports.jobs`。
- 页面模型：`frontend/src/pages/files/fileCenterPageModel.ts`、`frontend/src/pages/export/exportPageModel.ts`。
- API 范围：FILE-001 至 FILE-006、EXP-001 至 EXP-008，全部来自冻结契约 `docs/api.md` 与 `frontend/src/api/endpoints.ts`。
- 不包含增强项 `FILE-007` 分片上传，也不实现导入执行闭环。

## 文件 API 映射

| API | 页面能力 | 关键入参 | 权限/状态 |
| --- | --- | --- | --- |
| FILE-001 | 上传文件 | multipart `file`、bizType、moduleId、recordId、fieldCode、幂等键 | FILE_UPLOAD，存储配置可用 |
| FILE-002 | 文件列表 | systemId、分页筛选、status、bizType、moduleId、recordId、fieldCode | FILE_VIEW |
| FILE-003 | 文件详情 | fileId | 文件引用权限 |
| FILE-004 | 文件预览 | fileId | 文件引用权限，`previewable=false` 时禁用 |
| FILE-005 | 文件下载 | fileId | 文件引用权限，`downloadable=false` 时禁用；导出结果下载复用此入口 |
| FILE-006 | 删除文件 | fileId | FILE_DELETE，已删除/过期/存在引用时禁用 |

## 导出 API 映射

| API | 页面能力 | 关键入参 | 权限/状态 |
| --- | --- | --- | --- |
| EXP-001 | 导出模板列表 | systemId、moduleId | EXPORT_TEMPLATE_VIEW |
| EXP-002 | 新建导出模板 | moduleId、templateCode、templateName、fields | EXPORT_TEMPLATE_CREATE |
| EXP-003 | 更新导出模板 | templateId、moduleId、templateCode、templateName、fields | EXPORT_TEMPLATE_EDIT |
| EXP-004 | 创建导出任务 | moduleId、templateId、filters、sorter、selectedRecordIds、fileName、幂等键 | RECORD_EXPORT |
| EXP-005 | 导出任务列表 | systemId、moduleId、status、keyword、分页 | EXPORT_JOB_VIEW |
| EXP-006 | 导出任务详情 | jobId | EXPORT_JOB_VIEW |
| EXP-007 | 失败任务重试 | jobId、reason、幂等键 | EXPORT_JOB_RETRY，仅 `FAILED && retryable` 可用 |
| EXP-008 | 取消导出任务 | jobId、reason、幂等键 | EXPORT_JOB_CANCEL，`SUCCESS/CANCELED` 禁用 |

## 页面状态

- 文件页面统一输出 `FileRequestState`，导出页面统一输出 `ExportRequestState`，均保留 `requestId`、`retryable`、`errorMessage` 和本地 `validationErrors`。
- 上传成功后通过 `appendToDynamicFieldValue` 将 `fileId` 写入动态附件字段，字段值使用冻结约定 `FileBindDTO[]`。
- 文件预览和下载不绕过业务对象权限，页面仅在 `previewable/downloadable` 为 true 时开放入口，失败时展示后端原因和 requestId。
- 导出任务 `QUEUED`、`PROCESSING` 输出轮询建议；`SUCCESS` 暴露 `resultFileId` 并复用 FILE-005 下载；`FAILED` 展示 `failureReason`，只有 `retryable=true` 才允许重试。
- `FILE-001`、`EXP-004`、`EXP-007`、`EXP-008` 均通过 `X-Idempotency-Key` 和 body 幂等键约束，避免重复提交。

## 自检结论

- 已覆盖 FILE-001 至 FILE-006、EXP-001 至 EXP-008 的路由参数、请求体、响应体、权限点、状态值、幂等键、禁用态、空态/错误态和 requestId 展示。
- 页面模型没有新增旁路 `fetch`、`axios`、`XMLHttpRequest` 或手写 URL 请求，所有调用均走 `ApiClient.call` 与冻结端点 ID。
- 当前 `frontend/` 目录仍无 `package.json` 和 `tsconfig.json`，无法执行正式 build/typecheck；本任务按静态模型和契约证据完成。
