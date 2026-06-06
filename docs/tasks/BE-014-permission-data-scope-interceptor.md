# BE-014 权限与数据范围拦截

- taskId: BE-014
- 标题: 权限与数据范围拦截
- 负责角色: backend
- 所属大任务/模块: 后端 / 权限引擎
- 目标: 实现后端统一权限校验顺序、字段权限、数据范围和 OpenAPI scope 适配。
- 输入文件: `docs/api.md`、`docs/prd.md`、`backend/examine-core/`
- 输出文件或输出目录: `backend/examine-core/`、`backend/examine-module/`

## 详细工作内容

- 构建 RequestContext：accountId、systemId、tenantId、memberId、clientId、requestId、traceId。
- 按冻结顺序校验登录/OpenAPI、系统租户、成员角色、API/菜单、操作、字段、数据范围、状态、流程锁定。
- 提供 `EffectivePermissionVO`、字段权限和数据范围规则服务。

## 完成状态定义

- 默认状态: pending。
- 完成条件: 业务接口能统一调用权限服务和拦截器。

## 验收标准

- 后端权限校验不能被前端过滤替代。
- 字段无写权限保存必须返回明确错误。

## 测试/自检要求

- 测试菜单不可见、按钮无权限、字段不可写、数据范围越界、OpenAPI scope 越界。

## 依赖任务

- BE-002

## 可并行关系

- 可与 BE-003、GEN-001 并行，是 BE-005 及后续业务任务前置。

## 不允许事项

- 不在 Controller 中写业务权限拼装逻辑。
- 不跳过系统/租户上下文校验。

