# FE-010 文件与导出页面

- taskId: FE-010
- 标题: 文件与导出页面
- 负责角色: frontend
- 所属大任务/模块: 前端 / 文件导出
- 目标: 实现上传附件、文件中心、导出模板、导出任务和结果下载页面。
- 输入文件: `docs/api.md`、`frontend/src/api/`
- 输出文件或输出目录: `frontend/src/pages/files/`、`frontend/src/pages/export/`、`frontend/docs/page-contracts/FE-010-file-export-pages.md`

## 详细工作内容

- 实现 FILE-001 至 FILE-006 和 EXP-001 至 EXP-008 页面交互。
- 上传成功后将 `fileId` 写入动态字段，导出任务支持状态轮询、失败重试、取消和结果下载。
- 展示文件引用关系、失败原因、权限禁用和脱敏提示。
- 补齐页面级 API 映射证据，覆盖路由、API ID、必填入参、响应字段、上下文依赖、枚举/状态/错误码、权限禁用态、空态/错误态、requestId 展示和无旁路请求检查。

## 完成状态定义

- 默认状态: done。
- 完成条件: 文件附件和导出任务 MVP 闭环完成，`FE-010` 页面级契约证据文件已补齐。

## 完成记录

- 完成时间: 2026-06-07。
- 输出: `frontend/src/pages/files/fileCenterPageModel.ts`、`frontend/src/pages/files/index.ts`、`frontend/src/pages/export/exportPageModel.ts`、`frontend/src/pages/export/index.ts`、`frontend/docs/page-contracts/FE-010-file-export-pages.md`。
- 自检: 已覆盖 FILE-001 至 FILE-006、EXP-001 至 EXP-008 的页面模型、幂等键、动态附件字段写回、文件预览/下载/删除禁用态、导出任务轮询、失败重试/取消禁用态、requestId/错误态和页面契约证据；新增页面目录未发现旁路请求。
- 限制: 当前 `frontend/` 目录无 `package.json`/`tsconfig.json`，无法执行正式 build/typecheck。

## 验收标准

- 上传失败展示原因和 requestId。
- 导出成功刷新任务和结果文件入口。
- 权限禁用态、空态、错误态和完成证据可被 FE-012 汇总到 `frontend/docs/api-contract-map.md`。

## 测试/自检要求

- 自检文件类型限制、无权限下载、导出失败重试、任务轮询和取消。

## 依赖任务

- FE-007
- FE-008

## 可并行关系

- 可与 FE-009 并行。

## 不允许事项

- 不实现导入执行闭环。
- 不跳过业务对象权限展示下载入口。
