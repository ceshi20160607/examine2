# FE-001 typed SDK 与契约映射

- taskId: FE-001
- 标题: typed SDK 与契约映射
- 负责角色: frontend
- 所属大任务/模块: 前端 / SDK
- 目标: 基于冻结 API 建立 typed SDK、统一请求层、枚举、错误码和页面契约映射模板。
- 输入文件: `docs/api.md`、`docs/api_review.md`、`docs/service_info.md`
- 输出文件或输出目录: `frontend/src/api/`、`frontend/docs/page-contracts/_template.md`

## 详细工作内容

- 定义统一 `ApiResponse<T>`、分页、筛选、动态字段值、错误响应类型。
- 同步状态枚举、错误码命名空间和接口分组。
- 建立页面级 API 映射证据模板，供 FE-003 至 FE-011 分别补齐。

## 完成状态定义

- 默认状态: pending。
- 完成条件: SDK 可被页面引用，页面级契约映射模板存在；最终 `frontend/docs/api-contract-map.md` 只由 FE-012 汇总写入。

## 验收标准

- 页面不得直接散落 axios/fetch 调用。
- 枚举、状态值、错误码来源于冻结 API。

## 测试/自检要求

- 执行类型检查或 SDK 编译自检。
- 自检页面级契约映射模板覆盖路由、API ID、入参、响应字段、枚举/状态/错误码、权限禁用态、空态/错误态、requestId 和无旁路请求检查项。

## 依赖任务

- PLAN-001

## 可并行关系

- 不可并行；所有前端页面依赖 SDK。

## 不允许事项

- 不新增 API 文档没有的接口语义。
- 不把后端上下文字段暴露为前端可写。
