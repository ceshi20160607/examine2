# mobile/uniapp

UniApp（uni-ui）手机端工程。

## 本地开发

- **安装依赖**：

```powershell
corepack pnpm -C mobile/uniapp install
```

- **启动 H5**：

```powershell
corepack pnpm -C mobile/uniapp dev:h5
```

启动后打开终端输出的本地地址（类似 `http://localhost:5175/`）。

## 目前进度（Task 0）

- 已引入 `@dcloudio/uni-ui`（`pages.json` 已配置 easycom 自动扫描）
- 启动页：`pages/boot/health`
  - 通过 `src/api/http.ts` 请求后端（默认 `GET /v1/ping`）
- 登录页：`pages/auth/login`
  - 对接 `POST /v1/platform/auth/login`，登录成功保存 `token`

## 已实现功能（可跑通主流程）

- **认证/会话**：
  - 登录、退出、me、refresh（`/v1/platform/auth/*`）
  - HTTP 层自动携带 `Authorization`、`X-Request-Id`，401 自动 refresh 一次后重试
  - 进入系统后持久化 `SessionPayload`（systemId/tenantId…）
  - 全局守卫：未登录→登录；已登录未进入系统→系统列表
- **平台系统**：系统列表、创建系统、进入系统（`/v1/platform/systems` + `/v1/platform/context/enter-system`）
- **低代码（module）**：
  - Apps/Models/Fields 列表 + 创建（`/v1/system/module/meta/*`）
  - Records：列表、详情、创建、更新（`/v1/system/records*`）
  - 导出任务：列表/详情（`GET /v1/system/module/export-jobs/page`、`GET /v1/system/module/export-jobs/{jobId}`）
  - 导出模板/字段：`GET /v1/system/module/exports/models/{modelId}/tpls`、`POST /v1/system/module/exports/tpls/upsert`、`GET/POST /v1/system/module/exports/tpls/{tplId}/fields...`
- **流程（flow）**：Inbox（待办/抄送）+ Task 操作（同意/拒绝/领取/撤回/终止等）
  - 模板分页：`GET /v1/system/flow/temps/page`
  - 发起流程：`POST /v1/system/flow/instances/start`
  - 实例分页/详情：`GET /v1/system/flow/instances/page`、`GET /v1/system/flow/instances/{id}` + tasks/actions/traces
  - 我的相关实例分页：`GET /v1/system/flow/instances/my/page`
- **上传（upload）**：上传文件 + 列表分页
- **导航**：Tabbar（Workbench/Apps/Inbox/Me）

## 下一步（继续完善）

- **低代码**：字典、列表视图、RBAC（角色/菜单/权限）页面
- **流程**：实例查询、发起流程、流程模板列表
- **记录表单**：从 Field 元数据自动生成表单（替换当前 JSON 输入）
- **上传**：预览/下载、按业务挂载

