# mobile/uniapp

UniApp（uni-ui）手机端工程。

## 本地开发

- **安装依赖**：

```powershell
corepack pnpm -C mobile/uniapp install
```

- **H5 生产构建（发版前建议跑）**：

```powershell
corepack pnpm -C mobile/uniapp run build:h5
```

说明：`@dcloudio/uni-ui` 的组件样式会用到 `scss`；本仓库已在 `devDependencies` 中声明 `sass` 以满足构建。

- **启动 H5**：

```powershell
corepack pnpm -C mobile/uniapp dev:h5
```

启动后打开终端输出的本地地址（类似 `http://localhost:5175/`）。

## 工程说明

- 已引入 `@dcloudio/uni-ui`（`pages.json` 已配置 easycom 自动扫描）
- 启动页：`pages/boot/health`（`GET /v1/ping`）
- 登录页：`pages/auth/login`（`POST /v1/platform/auth/login`）

## 已实现功能（可跑通主流程）

- **认证/会话**：
  - 登录、退出、me、refresh（`/v1/platform/auth/*`）
  - HTTP 层自动携带 `Authorization`、`X-Request-Id`，401 自动 refresh 一次后重试
  - 进入系统后持久化 `SessionPayload`（systemId/tenantId…）
  - 全局守卫：未登录→登录；已登录未进入系统→系统列表
- **平台系统**：系统列表、创建系统、进入系统（`/v1/platform/systems` + `/v1/platform/context/enter-system`）
- **低代码（module）**：
  - Apps/Models/Fields 列表 + 创建（`/v1/system/module/meta/*`）
  - 字典/列表视图/筛选模板/列配置/导出/导出任务/导出字段：对应页面在 `src/pages/system/module/**`（以实际路由为准）
  - Records：列表、详情、创建、更新（`/v1/system/records*`；表单按字段元数据生成，支持 JSON 高级模式）
  - **RBAC**：角色/成员/菜单 + 角色菜单权限 + URI 权限预览（`/v1/system/auth/perm-preview` / `/v1/system/auth/permissions`）
  - 导出任务：列表/详情（`GET /v1/system/module/export-jobs/page`、`GET /v1/system/module/export-jobs/{jobId}`）
  - 导出模板/字段：`GET /v1/system/module/exports/models/{modelId}/tpls`、`POST /v1/system/module/exports/tpls/upsert`、`GET/POST /v1/system/module/exports/tpls/{tplId}/fields...`
  - 删除导出模板/字段：`POST /v1/system/module/exports/tpls/delete`、`POST /v1/system/module/exports/fields/delete`
  - 同步导出 CSV（下载）：`GET /v1/system/module/exports/tpls/{tplId}/export/csv?limit=200`
- **流程（flow）**：Inbox（待办/抄送）+ Task 操作（同意/拒绝/领取/撤回/终止等）
  - 模板管理/版本管理/子资源管理 + 版本发布 + 发起流程 + 按 biz 查询
  - 模板分页：`GET /v1/system/flow/temps/page`
  - 发起流程：`POST /v1/system/flow/instances/start`
  - 实例分页/详情：`GET /v1/system/flow/instances/page`、`GET /v1/system/flow/instances/{id}` + tasks/actions/traces
  - 我的相关实例分页：`GET /v1/system/flow/instances/my/page`
- **上传（upload）**：选择上传 + 列表分页 + 下载/预览/删除 + `X-Request-Id` 与鉴权头统一
- **导航**：Tabbar（Workbench/Apps/Inbox/Me）

## 上线前最小回归清单（建议按顺序）

- **准备**
  - 后端启动（Swagger/Knife4j 可访问）
  - 手机端配置好 `src/config/env.ts` 指向后端
- **认证/系统上下文**
  - 登录成功，能进入系统列表
  - 创建系统成功，并能“进入系统”（WorkBench 状态正常）
- **低代码（module）主链路**
  - 创建 App / Model / Field（至少：文本、数字、日期、字典单选/多选）
  - 创建 Record、编辑 Record、详情能正确展示
- **RBAC**
  - 创建角色、创建菜单（含 `apiPattern` / `permKey`）
  - 角色设置菜单权限、成员分配角色
  - 使用“权限验证（按 URI）”确认 `requiredPermKey/allowed` 符合预期
- **Flow**
  - 模板管理：创建模板 → 一键发布 MVP（或手动版本→发布）
  - 发起流程：选择模板 → 发起成功 → 打开任务 → 同意/拒绝 → 返回待办箱
- **Upload**
  - 上传文件成功，列表可见

## 下一步（非阻塞增强）

- **更完善的图形化工作流设计器**（目前以 graphJson/关系表 + MVP 跑通为主）
- **更完整的字段类型/校验规则**（复杂布局、级联、引用字段等）
- **更完整的权限模型表达**（细粒度到接口方法/多角色组合；当前以菜单 apiPattern + permKey 为主）

