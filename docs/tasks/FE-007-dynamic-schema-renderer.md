# FE-007 动态 schema 渲染组件

- taskId: FE-007
- 标题: 动态 schema 渲染组件
- 负责角色: frontend
- 所属大任务/模块: 前端 / 动态渲染
- 目标: 建立运行台列表、表单、详情、字段权限和错误定位的共用渲染能力。
- 输入文件: `docs/api.md`、`frontend/src/api/`
- 输出文件或输出目录: `frontend/src/components/dynamic-schema/`、`frontend/docs/page-contracts/FE-007-dynamic-schema-renderer.md`

## 详细工作内容

- 实现 TEXT、NUMBER、MONEY、DATE、DATETIME、SELECT、MULTI_SELECT、SWITCH、MEMBER、DEPT、ATTACHMENT、IMAGE、AUTO_NO、RELATION、SUB_TABLE、ADDRESS、TAG、JSON 等 MVP 字段类型渲染。
- 统一处理 `FieldPermission`、`AvailableAction`、`readonlyReason` 和字段级错误。
- 支持列表筛选、排序、表单校验、详情展示和历史快照展示。
- 补齐动态 schema 组件级契约证据，覆盖字段类型、响应字段、权限禁用态、空态/错误态、requestId 展示和无旁路请求检查。

## 完成状态定义

- 默认状态: pending。
- 完成条件: 运行台、流程、文件导出页面可复用 schema 组件，`FE-007` 组件级契约证据文件已补齐。

## 验收标准

- 字段不可写显示只读原因。
- 校验失败能定位到字段。

## 测试/自检要求

- 自检主要字段类型、权限禁用、动态错误定位和响应式布局。

## 依赖任务

- FE-006

## 可并行关系

- 不可并行；运行台、流程和文件导出页面依赖本任务。

## 不允许事项

- 不把后端字段权限只作为前端安全边界。
- 不在组件中硬编码业务模块字段。
