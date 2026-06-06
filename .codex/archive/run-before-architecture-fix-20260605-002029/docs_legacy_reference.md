# 旧项目参考

## 扫描范围

本次只读扫描范围：

- `docs/user_requirement.md`
- `docs/service_info.md`
- `../examine2/`

旧项目参考目录为 `E:\workspace\03_project\unique\java\examine2`。扫描到的主要目录包括：

- `backend/`：Spring Boot Maven 多模块后端。
- `web/vue3/`：Vue3 + Vite Web 管理端。
- `mobile/uniapp/`：UniApp 移动端。
- `docs/`、`doc/`：需求、架构、API、部署、SQL、重构方案等文档。
- `docs/sql/`、`backend/examine-web/src/main/resources/db/migration/`：SQL 与 Flyway 迁移脚本。
- `scripts/`：启动、部署、数据库修复、release、smoke 脚本。
- `tests/`：API smoke、Web Playwright、移动端测试占位和回归脚本。
- `dist/`、`logs/`、`tmp/`、`node_modules/`、`.git/` 等为历史运行或开发产物，仅识别为存在，不建议作为新项目输入。

## 旧项目模块结构

### 后端

`../examine2/backend/pom.xml` 声明 Maven 父工程，Java 21，Spring Boot 3.3.5，MyBatis-Plus 3.5.9，Knife4j 4.5.0。模块包括：

- `examine-core`：通用核心能力。
- `examine-plat`：平台账号、系统、租户、平台 RBAC、平台消息、登录日志、操作日志、平台配置。
- `examine-app`：对外应用、凭证、授权范围、IP 白名单、访问日志。
- `examine-module`：低代码应用、模块、字段、字典、部门、页面、菜单、业务记录、记录值、历史、关系、列表视图、导出、系统内 RBAC、集成映射。
- `examine-flow`：流程模板、版本、节点、连线、条件、运行实例、任务、参与人、变量、动作日志、轨迹日志、流程绑定。
- `examine-upload`：上传文件、分片、存储配置。
- `examine-web`：HTTP 入口、认证、系统上下文、系统态接口、OpenAPI、上传编排、Flyway migration、全局配置。

### Web 前端

`../examine2/web/vue3/package.json` 显示 Web 使用 Vue 3、Vue Router、Vite。主要结构：

- `src/router/index.js`：路由定义。
- `src/api/`：`http.js`、`platformAuth.js`、`platform.js`、`platformApp.js`、`meta.js`、`module.js`、`records.js`、`rbac.js`、`flow.js`、`flowBinding.js`、`pages.js`、`dept.js`、`upload.js`。
- `src/views/`：登录、注册、系统、应用、模块、字段、字典、部门、页面、列表视图、导出、记录、流程、权限、上传、OpenAPI 等页面。
- `src/layouts/`、`src/components/`、`src/store/`、`src/style.css`：布局、组件、会话和样式。

### 移动端

`../examine2/mobile/uniapp/package.json` 显示移动端使用 UniApp、Vue、Pinia、TypeScript、Vite。主要结构：

- `src/pages/auth/`：登录、注册。
- `src/pages/platform/`：系统列表、租户选择、OpenAPI 应用。
- `src/pages/system/records/`：记录列表、表单、详情、历史。
- `src/pages/system/flow/`：流程模板、版本、图编辑、发起、待办、实例、任务处理。
- `src/pages/system/module/`：模块元数据、字典、部门、页面、列表视图、导出、流程绑定、RBAC。
- `src/pages/system/runtime/`：运行入口和运行菜单。
- `src/api/`、`src/utils/`、`src/stores/`、`src/ui/`：API 封装、运行工具、会话状态、基础 UI。

### SQL、部署与测试

- `../examine2/docs/sql/`：平台、上传、模块、流程、OpenAPI、平台 RBAC、字段扩展、任务修复等 SQL。
- `../examine2/backend/examine-web/src/main/resources/db/migration/`：Flyway 迁移脚本，包含 `V1__platform_ddl.sql` 到 `V28__module_menu_acl_schema_repair.sql` 等版本。
- `../examine2/scripts/deploy/`：后端运行、前端启动、release 构建、Nginx 配置。
- `../examine2/tests/api/`：HTTP 端到端 smoke。
- `../examine2/tests/web/`：Playwright 管理端 UI 测试。
- `../examine2/tests/run-all.ps1`：整合回归脚本。

## 可参考的实体、接口、页面、配置或脚本

### 实体与表结构

平台与租户：

- 实体：`PlatAccount`、`PlatAccountRole`、`PlatConfig`、`PlatLoginLog`、`PlatMenu`、`PlatMsg`、`PlatOperLog`、`PlatRole`、`PlatRoleMenu`、`PlatSystem`、`PlatTenant`。
- 表：`un_plat_account`、`un_plat_config`、`un_plat_login_log`、`un_plat_oper_log`、`un_plat_msg`、`un_plat_system`、`un_plat_tenant`、`un_plat_menu`、`un_plat_role`、`un_plat_role_menu`、`un_plat_account_role`。

低代码模块：

- 实体：`ModuleApp`、`ModuleAppVersion`、`ModuleModel`、`ModuleField`、`ModuleFieldOption`、`ModuleDict`、`ModuleDictItem`、`ModuleDept`、`ModuleMenu`、`ModulePage`、`ModulePageBlock`、`ModuleListView`、`ModuleListViewCol`、`ModuleListFilterTpl`、`ModuleListFilterField`、`ModuleRelation`、`ModuleRecord`、`ModuleRecordData`、`ModuleRecordHistory`、`ModuleRole`、`ModuleMember`、`ModuleRoleMenuPerm`、`ModuleRoleActionPerm`、`ModuleRolePagePerm`、`ModuleRoleFieldPerm`、`ModuleExportTpl`、`ModuleExportTplField`、`ModuleExportJob`、`ModuleSerialSeq`、`ModuleIntegration`、`ModuleIntegrationEvent`、`ModuleIntegrationMapping`、`ModuleIndex`。
- 表：`un_module_app`、`un_module_app_version`、`un_module_model`、`un_module_field`、`un_module_field_option`、`un_module_dict`、`un_module_dict_item`、`un_module_dept`、`un_module_menu`、`un_module_page`、`un_module_page_block`、`un_module_list_view`、`un_module_list_view_col`、`un_module_list_filter_tpl`、`un_module_list_filter_field`、`un_module_relation`、`un_module_record`、`un_module_record_data`、`un_module_record_history`、`un_module_role`、`un_module_member`、`un_module_role_*_perm`、`un_module_export_tpl`、`un_module_export_tpl_field`、`un_module_export_job`、`un_module_serial_seq`、`un_module_integration*`、`un_module_index`。

流程：

- 实体：`FlowTemp`、`FlowTempVer`、`FlowTempVerNode`、`FlowTempVerLine`、`FlowTempVerLineCond`、`FlowTempVerSetting`、`FlowTempVerNodeSetting`、`FlowRecord`、`FlowRecordNode`、`FlowRecordLine`、`FlowRecordLineCond`、`FlowRecordSetting`、`FlowRecordNodeSetting`、`FlowRecordVar`、`FlowTask`、`FlowTaskActor`、`FlowBinding`、`FlowBizBinding`、`FlowActionLog`、`FlowLogTrace`。
- 表：`un_flow_temp`、`un_flow_temp_ver`、`un_flow_temp_ver_node`、`un_flow_temp_ver_line`、`un_flow_temp_ver_line_cond`、`un_flow_record`、`un_flow_record_node`、`un_flow_record_line`、`un_flow_record_line_cond`、`un_flow_task`、`un_flow_task_actor`、`un_flow_record_var`、`un_flow_binding`、`un_flow_log_action`、`un_flow_log_trace`。

上传与 OpenAPI：

- 上传实体：`UploadFile`、`UploadFilePart`、`UploadStorageConfig`；表：`un_upload_file`、`un_upload_file_part`、`un_upload_storage_config`。
- OpenAPI 实体：`AppClient`、`AppClientCredential`、`AppClientScope`、`AppIpWhitelist`、`AppAccessLog`；表：`un_app_client`、`un_app_client_credential`、`un_app_client_scope`、`un_app_ip_whitelist`、`un_app_access_log`。

### 接口参考

平台与认证：

- `AuthController`
- `PlatformContextController`
- `PlatformInboxController`
- `PlatSystemController`
- `PlatTenantController`
- `PlatAccountController`
- `PlatRoleController`
- `PlatMenuController`

系统内模块能力：

- `SystemModuleMetaController`
- `SystemModuleRecordController`
- `SystemModuleDictController`
- `SystemModuleDeptController`
- `SystemModulePageController`
- `SystemModuleListViewController`
- `SystemModuleRbacController`
- `SystemModuleAuthController`
- `SystemModuleFlowBindingController`
- `SystemModuleExportController`
- `SystemModuleExportJobController`

流程：

- `SystemFlowController`
- `SystemFlowInboxController`
- `SystemFlowQueryController`
- `SystemFlowTempController`
- `SystemFlowTempVerController`
- `SystemFlowTempVerGraphController`
- `FlowTaskController`
- `FlowTempController`
- `FlowTempVerController`
- `FlowRecordController`

上传与 OpenAPI：

- `SystemUploadController`
- `UploadFileController`
- `UploadFilePartController`
- `UploadStorageConfigController`
- `PlatformAppController`
- `OpenApiFlowController`
- `OpenApiModuleRecordController`
- `AppClientController`
- `AppClientCredentialController`
- `AppClientScopeController`
- `AppIpWhitelistController`
- `AppAccessLogController`

已识别的重要路径契约：

- 平台认证：`/v1/platform/auth/login`、`/v1/platform/auth/register`、`/v1/platform/auth/refresh`。
- 平台系统上下文：`/v1/platform/systems`、`/v1/platform/context/enter-system`。
- 系统模块元数据：`/v1/system/module/meta/**`。
- 系统字典：`/v1/system/module/dicts/**`。
- 系统列表视图：`/v1/system/module/list-views/**`。
- 系统导出：`/v1/system/module/exports/**`。
- 系统 RBAC：`/v1/system/module/rbac/**`。
- 系统记录：`/v1/system/records/**`。
- 系统流程：`/v1/system/flow/**`。
- 系统上传：`/v1/system/uploads`。
- 对外记录与流程：`/v1/open/records/**`、`/v1/open/flow/**`。

### 页面参考

Web 管理端页面：

- 平台与登录：`LoginView.vue`、`RegisterView.vue`、`SystemsView.vue`、`PlatformInboxView.vue`。
- 应用配置：`AppsView.vue`、`AppHubView.vue`、`ModelsView.vue`、`FieldsView.vue`、`DictsView.vue`、`DictItemsView.vue`、`DeptView.vue`、`RelationsView.vue`。
- 页面与运行：`PagesView.vue`、`PageEditView.vue`、`RuntimeMenusView.vue`、`RecordsListView.vue`、`RecordFormView.vue`、`RecordDetailView.vue`。
- 权限：`RbacView.vue`、`RoleMenusView.vue`、`RolePagesView.vue`。
- 流程：`FlowTempsView.vue`、`FlowTempDetailView.vue`、`FlowGraphDesignerView.vue`、`FlowInboxView.vue`、`FlowStartView.vue`、`FlowTaskView.vue`、`FlowInstancesView.vue`、`FlowInstanceDetailView.vue`。
- 文件、导出、开放应用：`UploadView.vue`、`ExportsView.vue`、`ExportJobsView.vue`、`OpenAppsView.vue`、`OpenAppDetailView.vue`。

移动端页面：

- `src/pages/auth/*`
- `src/pages/platform/*`
- `src/pages/system/records/*`
- `src/pages/system/flow/*`
- `src/pages/system/module/*`
- `src/pages/system/runtime/*`
- `src/pages/system/upload/*`
- `src/pages/tabs/*`

### 配置与脚本参考

- 后端配置：`../examine2/backend/examine-web/src/main/resources/application.yml`、`application-prod.yml`。
- Flyway：`../examine2/backend/examine-web/src/main/resources/db/migration/`。
- SQL 汇总：`../examine2/docs/sql/README.md`、`SQL-README.md`。
- 部署：`../examine2/docs/deploy/simple-run.md`、`production.md`、`flyway-existing-db.md`。
- 脚本：`../examine2/scripts/deploy/run-backend.ps1`、`start-frontend.ps1`、`build-release.ps1`、`scripts/startup-dev.ps1`、`start.bat`、`start.sh`。
- 测试：`../examine2/tests/api/e2e-smoke.ps1`、`../examine2/tests/web/playwright.config.ts`、`../examine2/tests/run-all.ps1`、`../examine2/tests/TEST-PLAN.md`。

## 不建议沿用的问题

- 不建议沿用旧项目页面组织方式。需求已明确旧页面散、配置态和使用态混杂，新项目应重构为平台中心、应用配置中心、应用运行台、流程工作台和运维中心。
- 不建议直接复用旧项目中文文档输出。当前扫描中多个 Markdown 在终端显示编码异常，后续应统一 UTF-8 编码并补齐中文说明。
- 不建议直接复制旧项目 SQL 作为新项目初始化脚本。旧项目 SQL 来源分散，且全量需求新增了配置版本、发布、回滚、KPI、仪表盘、打印模板、归档、权限解释、缓存、幂等、限流、密钥版本、审计等数据设计要求。
- 不建议直接沿用旧 EAV 查询模型作为全部列表查询基础。新项目需要考虑索引值表、typed value、常用筛选字段索引、统计缓存和大表性能。
- 不建议继承旧目录中的运行日志、dist 包、node_modules、tmp、zip、IDE 配置和 Git 工作区内容。
- 不建议把旧 OpenAPI 明文 SK 兼容模式作为主方案。新项目应优先 HMAC 签名、密钥加密存储、密钥版本、轮换、IP 白名单、限流、幂等和调用审计。
- 不建议只用 JSON 或列表编辑承载流程设计。新项目应按需求实现可视化设计、发布前校验、流程模拟、节点配置和运行时快照。
- 不建议只做基础 CRUD Controller。新项目应按 PRD 区分平台层、自定义系统层、运行台和 OpenAPI，入参、出参、权限、审计和异常码需要重新收敛。

## 与新项目实现相关的路径索引

需求与公共配置：

- `docs/user_requirement.md`
- `docs/service_info.md`

旧项目核心文档：

- `../examine2/README.md`
- `../examine2/doc/docs/architecture.md`
- `../examine2/docs/api/openapi-contract.md`
- `../examine2/docs/api/curl-examples.md`
- `../examine2/docs/rebuild1/rebuild_full_solution.md`
- `../examine2/docs/rebuild1/database_schema.md`
- `../examine2/docs/deploy/simple-run.md`
- `../examine2/tests/TEST-PLAN.md`

后端：

- `../examine2/backend/pom.xml`
- `../examine2/backend/examine-core/`
- `../examine2/backend/examine-plat/`
- `../examine2/backend/examine-app/`
- `../examine2/backend/examine-module/`
- `../examine2/backend/examine-flow/`
- `../examine2/backend/examine-upload/`
- `../examine2/backend/examine-web/`
- `../examine2/backend/examine-web/src/main/java/com/unique/examine/web/controller/`
- `../examine2/backend/examine-web/src/main/java/com/unique/examine/web/security/`
- `../examine2/backend/examine-web/src/main/resources/db/migration/`

数据库：

- `../examine2/docs/sql/01_platform_ddl.sql`
- `../examine2/docs/sql/03_upload_ddl.sql`
- `../examine2/docs/sql/05_module_ddl.sql`
- `../examine2/docs/sql/07_flow_ddl.sql`
- `../examine2/docs/sql/09_app_ddl.sql`
- `../examine2/docs/sql/12_plat_rbac_ddl.sql`
- `../examine2/docs/sql/17_module_record_data_typed_alter.sql`
- `../examine2/docs/sql/20_app_client_credential_sign_secret_enc.sql`

Web 前端：

- `../examine2/web/vue3/package.json`
- `../examine2/web/vue3/src/router/index.js`
- `../examine2/web/vue3/src/api/`
- `../examine2/web/vue3/src/views/`
- `../examine2/web/vue3/src/layouts/`
- `../examine2/web/vue3/src/components/`
- `../examine2/web/vue3/src/store/session.js`

移动端：

- `../examine2/mobile/uniapp/package.json`
- `../examine2/mobile/uniapp/src/pages.json`
- `../examine2/mobile/uniapp/src/api/`
- `../examine2/mobile/uniapp/src/pages/`
- `../examine2/mobile/uniapp/src/utils/`
- `../examine2/mobile/uniapp/src/stores/`
- `../examine2/mobile/uniapp/src/ui/`

部署与验证：

- `../examine2/scripts/deploy/run-backend.ps1`
- `../examine2/scripts/deploy/start-frontend.ps1`
- `../examine2/scripts/deploy/build-release.ps1`
- `../examine2/scripts/deploy/nginx/examine.conf`
- `../examine2/tests/api/e2e-smoke.ps1`
- `../examine2/tests/web/e2e/`
- `../examine2/tests/run-all.ps1`
