# 模块归属重构（examine-web 下沉到各模块）设计说明

> 目标：恢复“模块拆分”的意义——`examine-web` 只做入站层（HTTP/Filter/Interceptor/Advice/装配），领域实现归属回 `examine-module/examine-flow/examine-upload/examine-app/examine-plat`。

## 背景与问题

当前代码已能运行并通过编译，但出现了“领域实现落在入口层”的结构偏差：

- `backend/examine-web/src/main/java/com/unique/examine/web/manage/module/**` 存在 `module` 领域的手写业务实现（导出、导出任务与 Runner）。
- `backend/examine-web/src/main/java/com/unique/examine/web/service/**` 中存在多项应归属 `examine-module` 或 `examine-flow` 的领域/应用服务（例如 `FlowEngineService`、`ModuleRecordFacadeService` 等）。

这会导致：

- web 模块不断膨胀，模块拆分失去边界价值
- 领域依赖方向混乱（入口层承担领域编排与持久化实现）
- 后续若拆服务/独立部署将很难抽离

## 设计目标（验收口径）

1. `examine-web` **不再承载** `module/flow/upload/app/plat` 领域的核心业务实现，只保留：
   - `controller/**`（含 `controller/module/**`）
   - web 侧 `config/**`、Filter、Interceptor、Advice、鉴权入口与少量适配
2. 领域实现移动到对应模块，并统一放在 `manage/**` 包下：
   - lowcode/module → `examine-module: com.unique.examine.module.manage/**`
   - flow → `examine-flow: com.unique.examine.flow.manage/**`
   - upload → `examine-upload: com.unique.examine.upload.manage/**`（如有）
   - app(openapi client) → `examine-app: com.unique.examine.app.manage/**`
   - plat → `examine-plat: com.unique.examine.plat.manage/**`
3. `examine-web` 的 controller 只调用各模块暴露的 **Facade/Manage 服务**，不直接包含复杂业务。
4. 每个迁移点都能 `mvn test` 通过，并以“一个功能点一个 commit”提交。

## 范围与分批策略

### 第 1 批（最小闭环、影响面最小）

把 `examine-web/manage/module/**` 下沉到 `examine-module/manage/**`：

- `SystemModuleExportService`
- `SystemModuleExportJobService`
- `SystemModuleExportJobRunner`

并调整 `examine-web/controller/module/**` 仅注入并调用 `examine-module` 对应 manage 服务。

> 依赖注意：Runner 目前写入 `un_upload_file`，需要访问 upload 服务。若下沉到 `examine-module`，允许 `examine-module` 依赖 `examine-upload`（领域上合理：module 的导出结果以 upload 产物形式保存）。

### 第 2 批（module 领域应用服务回归）

把 `examine-web/service` 中明显属于 module 领域的服务下沉到 `examine-module/manage/**`，web 层保留薄 controller：

- `ModuleRecordFacadeService`
- `SystemModuleMetaService`
- `SystemModuleRbacService`
- `SystemModuleDictService`
- `SystemModuleListViewService`
- （如有）module 侧缓存桥接/ACL 计算等

### 第 3 批（flow 领域回归）

把 `examine-web/service` 中属于 flow 领域的引擎/图同步等下沉到 `examine-flow/manage/**`：

- `FlowEngineService`
- `FlowRecordGraphSyncService`
- `FlowBizActionService`（若属于 flow 侧的推进/后置动作编排）

并把 `examine-web/controller/SystemFlow*.java` 调整为注入 flow manage 服务。

## 边界规则（什么必须留在 web）

以下属于“入站层”，应留在 `examine-web`：

- HTTP 参数/响应映射、`ApiResult` 返回封装
- `Filter/Interceptor/Advice`（如 `RequestContextFilter`、日志、权限拦截器）
- OpenAPI 认证 Filter（依赖 servlet 请求/响应）
- Actuator / Flyway 的启动装配与配置（依赖启动应用模块）

## 兼容性与风险控制

- 包名变动会影响 Spring 扫描：下沉后必须确保对应模块被 `examine-web` 依赖并能被 component scan 扫到（或使用 `@Import`/自动配置）。
- 依赖方向必须保持单向：web 依赖各模块；领域模块之间依赖需要谨慎，但 `module -> upload` 属于合理依赖。
- 本仓库目前自动化测试较少：迁移阶段以 `mvn test`（编译级验证）为主；后续可补关键路径集成测试。

## 完成定义

当以下均满足时，本次“模块归属重构”完成：

- `examine-web/manage/**` 不再包含 `module` 领域实现（空或仅保留纯 web manage）
- module/flow 领域实现分别位于 `examine-module/manage/**`、`examine-flow/manage/**`
- 所有 controller 仍可编译运行
- 后端全模块 `mvn test` 通过

