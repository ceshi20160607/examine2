# 旧项目参考梳理

## 扫描范围

本次只读取以下输入：

- `docs/user_requirement.md`
- `docs/service_info.md`
- `.codex/oldexamine/`

旧项目参考目录固定为 `.codex/oldexamine/`。本次未读取其它旧项目目录，未修改旧项目任何文件。

重点扫描了旧项目父 POM 与以下子模块：

- `.codex/oldexamine/pom.xml`
- `.codex/oldexamine/examine-core`
- `.codex/oldexamine/examine-plat`
- `.codex/oldexamine/examine-module`
- `.codex/oldexamine/examine-flow`
- `.codex/oldexamine/examine-upload`
- `.codex/oldexamine/examine-app`
- `.codex/oldexamine/examine-web`

同时扫描了旧项目 SQL/Flyway 目录：

- `.codex/oldexamine/examine-web/src/main/resources/db/migration`

旧项目目录只包含后端 Maven 工程，未扫描到独立前端页面源码，例如 `.vue`、`.tsx`、`.ts`、`.js`、`.html`、样式文件或前端工程目录。

## 旧项目模块结构

旧项目是 Maven 多模块后端工程，父工程为 `com.unique:examine:0.0.1-SNAPSHOT`，Spring Boot 版本 `3.3.5`，Java 版本 `21`，MyBatis-Plus BOM 版本 `3.5.9`，Knife4j 版本 `4.5.0`。

| 模块 | 文件量概览 | 主要职责 | 新项目建议 |
| --- | --- | --- | --- |
| `examine-core` | Java 16、XML 1 | 通用返回、异常、上下文、MyBatis-Plus 自动填充、Session、平台/模块权限码和缓存协调 | 沿用核心公共模块定位，但需补齐统一错误码、上下文清理、公共安全工具和稳定注释编码 |
| `examine-plat` | Java 59、XML 12 | 平台账号、系统、租户、平台配置、消息、登录日志、操作日志、平台 RBAC | 作为平台中心基础，需区分平台账号与系统内成员，平台表前缀使用 `un_plat_` |
| `examine-app` | Java 28、XML 6 | OpenAPI 客户端、凭证、scope、IP 白名单、调用日志、签名工具 | 可参考 OpenAPI 安全能力；旧库表域为 `un_app_`，新项目 DB 设计应迁移为 `un_openapi_`，旧表名只作历史映射参考 |
| `examine-module` | Java 202、XML 34 | 应用、模块、字段、字典、页面、菜单、成员、部门、权限、记录、EAV、导出、关联和事件 | 是动态建模核心参考，必须重构为 `base` 与 `manage` 分层，避免照搬生成式 CRUD |
| `examine-flow` | Java 156、XML 31 | 流程模板、版本、节点、连线、条件、实例、任务、抄送、日志、流程引擎 | 可参考流程表、引擎推进和工作台能力，但需产品化流程设计器、发布检查和业务状态联动 |
| `examine-upload` | Java 15、XML 4 | 上传存储配置、文件主表、分片表、文件基础 CRUD | 可参考文件元数据和本地存储，需增强对象存储、引用关系、权限、补偿和安全 |
| `examine-web` | Java 58、XML 2、SQL 28、资源 2 | Spring Boot 启动、Web 配置、鉴权拦截、全局异常、日志、Flyway、平台/系统/OpenAPI 聚合 Controller | 应保留启动与 Web 装配定位，避免承载过多业务实现 |

## Maven 父子模块结构、包名结构和启动模块

### 父工程

`.codex/oldexamine/pom.xml`：

- `groupId`: `com.unique`
- `artifactId`: `examine`
- `packaging`: `pom`
- 父依赖：`org.springframework.boot:spring-boot-starter-parent:3.3.5`
- 子模块顺序：`examine-core`、`examine-plat`、`examine-app`、`examine-module`、`examine-flow`、`examine-upload`、`examine-web`
- 统一 Java 21、MyBatis-Plus BOM、Maven Compiler Plugin、Spring Boot Maven Plugin。

### 启动模块

启动类：

- `.codex/oldexamine/examine-web/src/main/java/com/unique/examine/ExamineWebApplication.java`

启动类特征：

- `@SpringBootApplication(scanBasePackages = "com.unique.examine")`
- `@EnableScheduling`
- `@MapperScan` 扫描 `plat`、`app`、`module`、`upload`、`flow` mapper。
- `examine-web/pom.xml` 依赖所有业务模块，并设置最终包名 `unexamine-0.0.1`。

### 包名结构

统一基础包名为 `com.unique.examine`。

| 模块 | 主要 package | 典型路径 |
| --- | --- | --- |
| `examine-core` | `com.unique.examine.core` | `common`、`config`、`entity`、`exception`、`mapper`、`module`、`security`、`service`、`web` |
| `examine-plat` | `com.unique.examine.plat` | `controller`、`entity/dto`、`entity/po`、`manage`、`mapper/xml`、`service/impl` |
| `examine-app` | `com.unique.examine.app` | `controller`、`entity/po`、`manage`、`mapper/xml`、`security`、`service/impl` |
| `examine-module` | `com.unique.examine.module` | `controller`、`entity/dto`、`entity/po`、`field`、`manage`、`mapper/xml`、`service/impl` |
| `examine-flow` | `com.unique.examine.flow` | `controller`、`entity/po`、`manage`、`mapper/xml`、`service/impl` |
| `examine-upload` | `com.unique.examine.upload` | `controller`、`entity/po`、`mapper/xml`、`service/impl` |
| `examine-web` | `com.unique.examine.web` | `common`、`config`、`controller`、`controller/module`、`dto`、`flyway`、`handler`、`logging`、`manage`、`security`、`service` |

## 每个后端模块职责和典型路径

### `examine-core`

职责：

- 统一响应模型：`ApiResult<T>`。
- 业务异常：`BusinessException`。
- Session 存储与上下文：`SessionService`、`SessionPayload`、`AuthContextHolder`、`ModuleAuthContextHolder`。
- MyBatis-Plus 自动填充：`MybatisPlusConfig`。
- 权限码和缓存协调：`PlatPermCodes`、`ModulePermCodes`、`ModuleAuthCacheCoordinator`。

典型路径：

- Controller：无业务 Controller。
- Service：`.codex/oldexamine/examine-core/src/main/java/com/unique/examine/core/service/SessionService.java`
- Config：`.codex/oldexamine/examine-core/src/main/java/com/unique/examine/core/config/MybatisPlusConfig.java`
- Security：`.codex/oldexamine/examine-core/src/main/java/com/unique/examine/core/security/AuthContextHolder.java`
- Web：`.codex/oldexamine/examine-core/src/main/java/com/unique/examine/core/web/ApiResult.java`

可复用方向：

- 支撑新项目统一返回、requestId、认证上下文、系统/租户上下文和权限上下文。
- 需要规避旧实现错误码简单、返回结构轻量、中文编码异常的问题。

### `examine-plat`

职责：

- 平台账号、系统、租户、配置、消息、登录日志、操作日志。
- 平台角色、菜单、账号角色、角色菜单。
- 平台注册、登录、token 刷新、默认角色绑定。

主要 package：

- `com.unique.examine.plat.controller`
- `com.unique.examine.plat.manage`
- `com.unique.examine.plat.entity.po`
- `com.unique.examine.plat.mapper`
- `com.unique.examine.plat.service`

典型路径：

- Controller：`.codex/oldexamine/examine-plat/src/main/java/com/unique/examine/plat/controller/PlatAccountController.java`
- Manage：`.codex/oldexamine/examine-plat/src/main/java/com/unique/examine/plat/manage/PlatAuthManageService.java`
- Manage：`.codex/oldexamine/examine-plat/src/main/java/com/unique/examine/plat/manage/PlatRbacManageService.java`
- Entity：`.codex/oldexamine/examine-plat/src/main/java/com/unique/examine/plat/entity/po/PlatSystem.java`
- Mapper：`.codex/oldexamine/examine-plat/src/main/java/com/unique/examine/plat/mapper/PlatSystemMapper.java`
- XML：`.codex/oldexamine/examine-plat/src/main/java/com/unique/examine/plat/mapper/xml/PlatSystemMapper.xml`
- Service：`.codex/oldexamine/examine-plat/src/main/java/com/unique/examine/plat/service/IPlatSystemService.java`
- ServiceImpl：`.codex/oldexamine/examine-plat/src/main/java/com/unique/examine/plat/service/impl/PlatSystemServiceImpl.java`

可复用方向：

- 支撑平台中心、我的系统、系统启停、租户、平台账号角色、平台日志。
- 新项目应保留 `un_plat_` 表前缀，不得使用变体平台前缀。
- 旧 CRUD Controller 可用于识别表和基础服务，不建议作为对外接口。

### `examine-app`

职责：

- OpenAPI 客户端、凭证、scope、IP 白名单、访问日志。
- AK/SK、HMAC 签名、签名密钥加密和 secret 轮换。
- 平台侧创建、更新、启停、删除、轮换 OpenAPI client。

主要 package：

- `com.unique.examine.app.controller`
- `com.unique.examine.app.manage`
- `com.unique.examine.app.security`
- `com.unique.examine.app.entity.po`
- `com.unique.examine.app.mapper`
- `com.unique.examine.app.service`

典型路径：

- Controller：`.codex/oldexamine/examine-app/src/main/java/com/unique/examine/app/controller/AppClientController.java`
- Manage：`.codex/oldexamine/examine-app/src/main/java/com/unique/examine/app/manage/PlatformAppManageService.java`
- Security：`.codex/oldexamine/examine-app/src/main/java/com/unique/examine/app/security/OpenApiSignatureSupport.java`
- Entity：`.codex/oldexamine/examine-app/src/main/java/com/unique/examine/app/entity/po/AppClient.java`
- Mapper：`.codex/oldexamine/examine-app/src/main/java/com/unique/examine/app/mapper/AppClientMapper.java`
- XML：`.codex/oldexamine/examine-app/src/main/java/com/unique/examine/app/mapper/xml/AppClientMapper.xml`
- ServiceImpl：`.codex/oldexamine/examine-app/src/main/java/com/unique/examine/app/service/impl/AppClientServiceImpl.java`

可复用方向：

- 支撑外部系统接入、凭证轮换、OpenAPI 签名、幂等和日志审计。
- 需要补齐授权范围、IP 白名单执行、限流、调用日志字段、系统内 OpenAPI 与平台 OpenAPI 的边界。

### `examine-module`

职责：

- 应用、模块/模型、字段、字段选项、字典、字典项。
- 页面、页面块、菜单、列表视图、筛选模板。
- 成员、部门、角色、角色菜单/页面/字段/操作权限、数据范围。
- 动态记录主表、EAV 字段值、记录历史、关联关系、集成事件。
- 导出模板、导出字段、导出任务、后台导出 runner。
- 自动编号、字段类型注册、关联记录、流程触发。

主要 package：

- `com.unique.examine.module.controller`
- `com.unique.examine.module.manage`
- `com.unique.examine.module.field`
- `com.unique.examine.module.entity.dto`
- `com.unique.examine.module.entity.po`
- `com.unique.examine.module.mapper`
- `com.unique.examine.module.service`

典型路径：

- Controller：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/controller/ModuleFieldController.java`
- Manage：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/manage/SystemModuleMetaService.java`
- Manage：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/manage/ModuleRecordFacadeService.java`
- Manage：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/manage/SystemModuleRbacService.java`
- Manage：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/manage/ModuleDataScopeService.java`
- Field：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/field/ModuleFieldTypeRegistry.java`
- DTO：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/entity/dto/ModuleRecordDslQuery.java`
- Entity：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/entity/po/ModuleRecordData.java`
- Mapper：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/mapper/ModuleRecordDataMapper.java`
- XML：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/mapper/xml/ModuleRecordMapper.xml`
- ServiceImpl：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/service/impl/ModuleRecordServiceImpl.java`

可复用方向：

- 支撑应用配置中心、模块建模、运行台、数据记录、字段值、权限、部门成员、导出。
- 新项目应重点参考领域对象和部分 manage 编排思路，不照搬生成式 Controller。
- EAV、权限、字段类型、导出和关联关系需要重新做性能、版本、权限和 API 契约设计。

### `examine-flow`

职责：

- 流程模板、版本、节点、连线、条件、节点设置、流程记录、任务、任务候选人、变量、动作日志、轨迹日志。
- 流程设计图保存和 graphJson 互转。
- 流程引擎发起、同意、拒绝、撤回、终止、转交、领取、取消领取、子流程推进。
- 待办、抄送、我的申请和流程实例查询。

主要 package：

- `com.unique.examine.flow.controller`
- `com.unique.examine.flow.manage`
- `com.unique.examine.flow.entity.po`
- `com.unique.examine.flow.mapper`
- `com.unique.examine.flow.service`

典型路径：

- Controller：`.codex/oldexamine/examine-flow/src/main/java/com/unique/examine/flow/controller/FlowTaskController.java`
- Manage：`.codex/oldexamine/examine-flow/src/main/java/com/unique/examine/flow/manage/FlowEngineService.java`
- Manage：`.codex/oldexamine/examine-flow/src/main/java/com/unique/examine/flow/manage/FlowTempVerGraphService.java`
- Manage：`.codex/oldexamine/examine-flow/src/main/java/com/unique/examine/flow/manage/FlowTaskInboxService.java`
- Entity：`.codex/oldexamine/examine-flow/src/main/java/com/unique/examine/flow/entity/po/FlowRecord.java`
- Mapper：`.codex/oldexamine/examine-flow/src/main/java/com/unique/examine/flow/mapper/FlowTaskMapper.java`
- XML：`.codex/oldexamine/examine-flow/src/main/java/com/unique/examine/flow/mapper/xml/FlowTaskMapper.xml`
- ServiceImpl：`.codex/oldexamine/examine-flow/src/main/java/com/unique/examine/flow/service/impl/FlowTaskServiceImpl.java`

可复用方向：

- 支撑流程工作台、流程设计器、审批运行、实例查询、待办/抄送。
- 需要重构审批原因强制、发布完整性检查、业务状态联动、幂等和并发防重复。
- 旧实体存在部分未在 Flyway DDL 中确认的表映射，DBA 不能无审查照搬。

### `examine-upload`

职责：

- 文件元数据、存储配置、分片表的基础 CRUD。
- `examine-web` 中的 `SystemUploadController` 承载实际上传、下载、预览和软删除接口。

主要 package：

- `com.unique.examine.upload.controller`
- `com.unique.examine.upload.entity.po`
- `com.unique.examine.upload.mapper`
- `com.unique.examine.upload.service`

典型路径：

- Controller：`.codex/oldexamine/examine-upload/src/main/java/com/unique/examine/upload/controller/UploadFileController.java`
- Web Controller：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/controller/SystemUploadController.java`
- Entity：`.codex/oldexamine/examine-upload/src/main/java/com/unique/examine/upload/entity/po/UploadFile.java`
- Entity：`.codex/oldexamine/examine-upload/src/main/java/com/unique/examine/upload/entity/po/UploadStorageConfig.java`
- Mapper：`.codex/oldexamine/examine-upload/src/main/java/com/unique/examine/upload/mapper/UploadFileMapper.java`
- XML：`.codex/oldexamine/examine-upload/src/main/java/com/unique/examine/upload/mapper/xml/UploadFileMapper.xml`
- ServiceImpl：`.codex/oldexamine/examine-upload/src/main/java/com/unique/examine/upload/service/impl/UploadFileServiceImpl.java`

可复用方向：

- 支撑附件字段、文件中心、导出结果下载、对象存储配置扩展。
- 需要增强引用关系、分片合并、对象存储、权限校验、文件安全和事务补偿。

### `examine-web`

职责：

- Spring Boot 启动和依赖聚合。
- WebMVC、过滤器、拦截器、OpenAPI 配置。
- Token 鉴权、系统上下文、模块权限、OpenAPI 鉴权。
- 全局异常、requestId、日志链路。
- Flyway migration、启动时 schema repair、手工标记 Flyway 版本。
- 平台认证、系统态模块、系统态流程、系统态上传、OpenAPI 记录/流程等聚合 Controller。

主要 package：

- `com.unique.examine.web.config`
- `com.unique.examine.web.security`
- `com.unique.examine.web.controller`
- `com.unique.examine.web.controller.module`
- `com.unique.examine.web.handler`
- `com.unique.examine.web.logging`
- `com.unique.examine.web.flyway`
- `com.unique.examine.web.service`

典型路径：

- 启动类：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/ExamineWebApplication.java`
- 配置：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/config/WebMvcConfig.java`
- 鉴权：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/security/TokenAuthenticationFilter.java`
- OpenAPI 鉴权：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/security/OpenApiAuthenticationFilter.java`
- 系统上下文：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/security/SystemContextInterceptor.java`
- 模块权限：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/security/ModuleApiPathPermissionInterceptor.java`
- 异常：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/handler/GlobalExceptionHandler.java`
- Flyway：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/config/FlywayStartupConfig.java`
- 业务聚合 Controller：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/controller/SystemModuleRecordController.java`
- 导出聚合 Controller：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/controller/module/SystemModuleExportController.java`

可复用方向：

- 支撑新项目启动模块、Web 装配、认证拦截、requestId、全局异常、OpenAPI 文档和健康检查。
- 不建议把旧项目中大量系统态业务 Controller 继续放在 `examine-web`；应下沉到业务模块 manage controller 或清晰聚合入口。

## 表命名规则、表前缀与 Flyway/SQL migration 组织方式

### 表前缀规则

旧项目统一使用 `un_` 开头，并按模块分前缀：

| 前缀 | 模块 | 典型表 |
| --- | --- | --- |
| `un_plat_` | 平台、系统、租户、平台 RBAC、平台日志 | `un_plat_account`、`un_plat_system`、`un_plat_tenant`、`un_plat_menu`、`un_plat_role` |
| `un_module_` | 应用、模块、字段、页面、菜单、权限、记录、导出、部门成员 | `un_module_app`、`un_module_model`、`un_module_field`、`un_module_record`、`un_module_record_data` |
| `un_flow_` | 流程模板、版本、节点、实例、任务、日志 | `un_flow_temp`、`un_flow_temp_ver`、`un_flow_record`、`un_flow_task` |
| `un_upload_` | 上传、文件、存储配置、分片 | `un_upload_file`、`un_upload_file_part`、`un_upload_storage_config` |
| `un_app_` | OpenAPI 客户端、凭证、scope、IP 白名单、访问日志的历史表域 | 旧表 `un_app_client`、`un_app_client_credential`、`un_app_access_log` 只作历史参考；新项目 OpenAPI 表统一迁移到 `un_openapi_` |

平台模块表前缀必须使用 `un_plat_`，不得使用变体平台前缀。

### Flyway 目录

旧项目 Flyway 目录：

- `.codex/oldexamine/examine-web/src/main/resources/db/migration`

迁移文件按版本组织，DDL 与 seed 混排：

| 文件 | 主要内容 |
| --- | --- |
| `V1__platform_ddl.sql` | 平台基础表：账号、系统、租户、配置、消息、登录日志、操作日志 |
| `V2__platform_seed.sql` | 平台系统和租户种子 |
| `V3__upload_ddl.sql` | 上传文件、分片、存储配置 |
| `V4__upload_seed.sql` | 上传默认存储配置 |
| `V5__module_ddl.sql` | 应用、模型、字段、页面、菜单、字典、记录、权限、导出、集成等模块表 |
| `V6__module_seed.sql` | 默认应用、页面、菜单、模型、字段、角色 |
| `V7__flow_ddl.sql` | 流程模板、版本、节点、连线、记录、任务、变量、日志 |
| `V8__flow_seed.sql` | 流程模板种子 |
| `V9__app_ddl.sql` | OpenAPI client、credential、scope、IP 白名单、访问日志 |
| `V10__app_seed.sql` | OpenAPI 默认 client 和 scope |
| `V11__plat_rbac_ddl.sql` | 平台 RBAC 菜单、角色、角色菜单、账号角色 |
| `V12__plat_rbac_seed.sql` | 平台角色和菜单种子 |
| `V13__plat_rbac_backfill_account_role.sql` | 平台账号角色回填 |
| `V14__module_field_ref.sql` | 模块字段关联模型字段 |
| `V15__module_field_config_json.sql` | 模块字段配置 JSON |
| `V16__module_field_relation_label.sql` | 关联模块展示标签 |
| `V17__module_serial_seq.sql` | 自动编号序号表 |
| `V18__module_dept.sql` | 系统内部门表 |
| `V19__module_member_dept.sql` | 成员部门字段 |
| `V20__module_dept_depth.sql` | 部门层级路径 |
| `V21__openapi_sign_secret_eav_index.sql` | OpenAPI 签名密钥、EAV 索引 |
| `V22__plat_admin_seed.sql` | 平台管理员种子 |
| `V23__module_record_data_typed.sql` | EAV typed columns |
| `V24__module_role_data_scope.sql` | 角色数据范围 |
| `V25__module_record_data_eav_columns.sql` | EAV 字段修复 |
| `V26__flow_task_column_align.sql` | 流程任务字段对齐 |
| `V27__app_client_credential_sign_secret_enc.sql` | OpenAPI 签名密钥密文 |
| `V28__module_menu_acl_schema_repair.sql` | 菜单权限 schema 修复 |

### Flyway 风险

- 旧项目 `application.yml` 中启用了 Flyway，并设置 `baseline-on-migrate: true`、默认 `baseline-version: 22`、`validate-on-migrate: false`、`repair-on-migrate: true`。
- `FlywayStartupConfig` 会先运行 `SchemaCompatibilityRepair.repair(dataSource)`，再手工标记部分 migration 已成功，然后 `flyway.repair()` 和 `flyway.migrate()`。
- `SchemaCompatibilityRepair` 会运行时补建或补字段，例如 `un_module_app`、`un_module_menu`、`un_module_field`、`un_module_dept`、`un_module_record_data`、`un_module_role`、`un_flow_task`、`un_app_client_credential`。
- `FlywayManualMark` 默认手工标记版本 `14`，并支持 `23`。

新项目建议：

- 可以参考旧 migration 的分模块组织，但不要复制运行时静默补 schema 的做法。
- DBA 应输出完整 `sql/init.sql` 和清晰 migration，避免依赖手工标记版本。
- 对旧项目实体与 migration 不一致处必须重新审表。

## 旧项目表清单摘要

### 平台 `un_plat_`

- `un_plat_account`
- `un_plat_account_role`
- `un_plat_config`
- `un_plat_login_log`
- `un_plat_menu`
- `un_plat_msg`
- `un_plat_oper_log`
- `un_plat_role`
- `un_plat_role_menu`
- `un_plat_system`
- `un_plat_tenant`

### 模块 `un_module_`

- `un_module_action`
- `un_module_app`
- `un_module_app_version`
- `un_module_dept`
- `un_module_dict`
- `un_module_dict_item`
- `un_module_export_job`
- `un_module_export_tpl`
- `un_module_export_tpl_field`
- `un_module_field`
- `un_module_field_option`
- `un_module_index`
- `un_module_integration`
- `un_module_integration_event`
- `un_module_integration_mapping`
- `un_module_list_filter_field`
- `un_module_list_filter_tpl`
- `un_module_list_view`
- `un_module_list_view_col`
- `un_module_member`
- `un_module_menu`
- `un_module_model`
- `un_module_model_action`
- `un_module_page`
- `un_module_page_block`
- `un_module_record`
- `un_module_record_data`
- `un_module_record_history`
- `un_module_relation`
- `un_module_role`
- `un_module_role_action_perm`
- `un_module_role_field_perm`
- `un_module_role_menu_perm`
- `un_module_role_page_perm`
- `un_module_serial_seq`

实体映射还包含但已扫描 Flyway DDL 未确认建表：

- `un_module_role_perm`

该实体只能作为旧权限模型演进线索，不得直接纳入新 DB 设计。

### 流程 `un_flow_`

Flyway DDL 确认创建：

- `un_flow_binding`
- `un_flow_form_temp`
- `un_flow_log_action`
- `un_flow_log_trace`
- `un_flow_node_temp`
- `un_flow_record`
- `un_flow_record_line`
- `un_flow_record_line_cond`
- `un_flow_record_node`
- `un_flow_record_node_setting`
- `un_flow_record_setting`
- `un_flow_record_var`
- `un_flow_task`
- `un_flow_task_actor`
- `un_flow_temp`
- `un_flow_temp_ver`
- `un_flow_temp_ver_line`
- `un_flow_temp_ver_line_cond`
- `un_flow_temp_ver_node`
- `un_flow_temp_ver_node_setting`
- `un_flow_temp_ver_setting`

实体映射还包含但已扫描 Flyway DDL 未确认建表：

- `un_flow_action_log`
- `un_flow_biz_binding`
- `un_flow_definition`
- `un_flow_definition_version`
- `un_flow_form_template`
- `un_flow_instance`
- `un_flow_instance_trace`
- `un_flow_instance_variable`
- `un_flow_node_template`

这类实体/迁移不一致必须作为 DBA 风险重新核对。

### 上传 `un_upload_`

- `un_upload_file`
- `un_upload_file_part`
- `un_upload_storage_config`

### OpenAPI 历史 `un_app_`

- `un_app_access_log`
- `un_app_client`
- `un_app_client_credential`
- `un_app_client_scope`
- `un_app_ip_whitelist`

## DBA 可直接使用的旧表参考边界（PU-018 补充）

本节用于关闭 `DBA-PRD-001 / PU-018`，仅说明旧项目表域对新 DB 设计的参考边界。结论先行：

- 新 DB 设计只能把旧项目作为业务范围、字段线索、索引线索和风险反例参考，不得直接按旧实体包全量生成表。
- DBA 只能把 Flyway DDL 已确认建表的旧表作为“可参考”来源；未被 Flyway DDL 确认的旧实体表名必须进入“禁止直接沿用”清单。
- 旧 `un_app_` 是 OpenAPI 能力的历史表域，新项目 OpenAPI 表名前缀应统一改为 `un_openapi_`，避免与业务应用表 `un_module_app` 混淆。
- 旧项目存在运行时 `SchemaCompatibilityRepair` 和 Flyway 手工标记策略，这类策略只作为迁移治理反例，不作为新项目 DB 初始化方式。

### 按模块分类的旧表边界

| 模块 | 可参考旧表 | 可支撑的新项目场景 | 需重命名旧表 | 禁止直接沿用 | DBA 注意事项 |
| --- | --- | --- | --- | --- | --- |
| 平台 `examine-plat` | `un_plat_account`、`un_plat_system`、`un_plat_tenant`、`un_plat_config`、`un_plat_msg`、`un_plat_login_log`、`un_plat_oper_log`、`un_plat_menu`、`un_plat_role`、`un_plat_role_menu`、`un_plat_account_role` | 平台登录、我的系统、系统/租户、平台 RBAC、平台消息、平台日志和平台运维审计 | 无；平台前缀固定 `un_plat_` | 任何 `un_platt_` 或其它平台前缀变体；把 `un_plat_account` 直接当系统内成员表使用 | 平台账号是全局登录主体，系统内成员应落在模块/系统成员域；旧表字段可参考，但要补充账号锁定、密码安全、审计和系统停用访问规则 |
| 动态模块 `examine-module` | `un_module_app`、`un_module_app_version`、`un_module_model`、`un_module_field`、`un_module_field_option`、`un_module_dict`、`un_module_dict_item`、`un_module_page`、`un_module_page_block`、`un_module_menu`、`un_module_list_view`、`un_module_list_view_col`、`un_module_list_filter_tpl`、`un_module_list_filter_field`、`un_module_record`、`un_module_record_data`、`un_module_record_history`、`un_module_relation`、`un_module_index`、`un_module_serial_seq`、`un_module_dept`、`un_module_member`、`un_module_role`、`un_module_role_menu_perm`、`un_module_role_action_perm`、`un_module_role_page_perm`、`un_module_role_field_perm`、`un_module_export_tpl`、`un_module_export_tpl_field`、`un_module_export_job`、`un_module_action`、`un_module_model_action`、`un_module_integration`、`un_module_integration_event`、`un_module_integration_mapping` | 应用配置、模块建模、字段设计、页面/菜单、运行态记录、EAV 字段值、权限、导出、事件扩展和系统内组织角色 | 无强制重命名；如拆分导入、仪表盘、审计等新增能力，应按新 PRD 单独设计表名 | `un_module_role_perm`：Java 实体存在，Flyway 未建表，只在 `un_module_menu.perm_key` 注释中被提到，不能直接纳入新库 | 权限优先参考已建的菜单/动作/页面/字段权限表；EAV 表需重新设计 typed columns、索引、字段删除后的历史展示、唯一性和归档策略 |
| 流程 `examine-flow` | Flyway DDL 确认的 `un_flow_temp`、`un_flow_temp_ver`、`un_flow_temp_ver_node`、`un_flow_temp_ver_line`、`un_flow_temp_ver_line_cond`、`un_flow_temp_ver_setting`、`un_flow_temp_ver_node_setting`、`un_flow_node_temp`、`un_flow_form_temp`、`un_flow_binding`、`un_flow_record`、`un_flow_record_node`、`un_flow_record_line`、`un_flow_record_line_cond`、`un_flow_record_setting`、`un_flow_record_node_setting`、`un_flow_record_var`、`un_flow_task`、`un_flow_task_actor`、`un_flow_log_action`、`un_flow_log_trace` | 流程模板、版本、节点、连线、条件、表单/节点模板、模块绑定、流程实例记录、任务、候选人、变量、动作日志和轨迹日志 | 可选重命名需由 DBA 基于 PRD 重新设计：旧 `temp/temp_ver/record` 是缩写式命名，可映射为 template/version/instance 语义，但不能直接套用未建表实体名 | `un_flow_action_log`、`un_flow_biz_binding`、`un_flow_definition`、`un_flow_definition_version`、`un_flow_form_template`、`un_flow_instance`、`un_flow_instance_trace`、`un_flow_instance_variable`、`un_flow_node_template` | 只能把 V7 DDL 与 V26 补丁确认过的表作为参考；新库需统一“模板/定义、记录/实例、日志”命名口径，不得同时保留两套同义表 |
| 上传 `examine-upload` | `un_upload_file`、`un_upload_file_part`、`un_upload_storage_config` | 附件字段、文件中心、导出结果文件、本地存储到对象存储扩展 | 无强制重命名 | 不建议沿用只保存本地路径、缺少引用关系和安全治理的旧字段组合 | 新库应补文件引用表、临时/已引用/删除状态、对象存储配置、下载权限、分片合并、失败补偿和安全扫描边界 |
| OpenAPI `examine-app` | 旧 `un_app_client`、`un_app_client_credential`、`un_app_client_scope`、`un_app_ip_whitelist`、`un_app_access_log` 的字段、索引和安全语义可参考 | 外部系统接入、客户端、凭证、scope、IP 白名单、签名、幂等和调用日志 | 必须迁移为 `un_openapi_client`、`un_openapi_client_credential`、`un_openapi_client_scope`、`un_openapi_ip_whitelist`、`un_openapi_access_log` | 新 DB 禁止继续创建 OpenAPI 用途的 `un_app_*` 表；禁止把 `un_app_` 与业务应用 `un_module_app` 混用 | 后端模块名仍可保留 `examine-app`，但 DB 表域应叫 `un_openapi_`；`sign_secret_enc` 来自 V21/V27 补丁，需纳入新凭证表设计，明文 SK 不落库 |
| Web/Flyway 迁移治理 | `V1` 到 `V28` 的分模块 migration 组织方式可参考 | 新项目初始化 SQL、后续 migration、代码生成表到模块映射 | 不适用 | `SchemaCompatibilityRepair`、`FlywayManualMark`、运行时静默补表补列、手工标记 migration 成功 | 新项目必须输出清晰 `sql/init.sql` 和 migration，不得用运行时修复替代 DB 设计 |

### 旧 `un_app_` 到新 `un_openapi_` 命名映射

旧项目 `examine-app` 模块负责 OpenAPI 能力，Flyway 文件 `V9__app_ddl.sql` 使用了 `un_app_` 表前缀。结合 PRD 决策，新项目保留后端模块名 `examine-app` 作为 OpenAPI 模块，但 DB 表域统一迁移到 `un_openapi_`。

| 旧表 | 新项目建议表 | 历史来源 | 迁移注意事项 |
| --- | --- | --- | --- |
| `un_app_client` | `un_openapi_client` | OpenAPI 客户端主表 | 保留 `system_id`、`tenant_id`、`client_code` 唯一性思路；字段注释、索引名和外键注释同步从 app 改为 openapi |
| `un_app_client_credential` | `un_openapi_client_credential` | AK/SK 凭证表 | 合并 `secret_hash` 与 `sign_secret_enc` 设计；明文 secret 不落库；凭证轮换和过期状态需明确 |
| `un_app_client_scope` | `un_openapi_client_scope` | 授权范围表 | scope 需要按系统、租户、模块、动作、字段返回权限细化，不只保存字符串 |
| `un_app_ip_whitelist` | `un_openapi_ip_whitelist` | IP 白名单表 | CIDR 校验、启停状态、命中日志和默认拒绝策略需在 DB/API 阶段明确 |
| `un_app_access_log` | `un_openapi_access_log` | 对外访问日志表 | 日志量可能较大，需考虑索引、归档、requestId、错误码、耗时、客户端和 accessKey 维度 |

注意事项：

- 新项目不得再用 `un_app_*` 表表达 OpenAPI，避免和 `un_module_app` 表达的业务应用概念冲突。
- 旧 `V21__openapi_sign_secret_eav_index.sql`、`V27__app_client_credential_sign_secret_enc.sql` 都在补 `un_app_client_credential.sign_secret_enc`，说明旧迁移存在基线跳版本和补丁重复风险；新 DB 设计应一次性定义完整凭证字段。
- 旧 `un_app_access_log` 字段可参考，但新项目应补充 OpenAPI 错误码命名空间、幂等键、限流命中、scope 命中和数据范围审计字段。
- 如后续需要历史数据迁移，迁移脚本应显式从 `un_app_*` 读出并写入 `un_openapi_*`，不得在新库保留双前缀并行。

### Flow 实体与 Flyway DDL 核对结论

已核对旧项目 flow 表来源：

- Flyway DDL 来源：`.codex/oldexamine/examine-web/src/main/resources/db/migration/V7__flow_ddl.sql`。
- Flow 补丁来源：`.codex/oldexamine/examine-web/src/main/resources/db/migration/V26__flow_task_column_align.sql`。
- Java 实体来源：`.codex/oldexamine/examine-flow/src/main/java/com/unique/examine/flow/entity/po/`。

核对结论：

- V7 DDL 创建了 21 张 `un_flow_` 表，实体包中这 21 张均有对应实体，属于“可参考”范围。
- V26 仅对 `un_flow_task` 做兼容对齐：旧库如存在 `instance_id/node_id`，会调整为 `record_id/node_key`，并重建 `idx_flow_task_record`。这说明旧 flow 任务表经历过命名修补，新设计应直接采用明确字段，不应继承补丁式迁移。
- 实体包额外存在 9 张 Flyway DDL 未确认建表的映射：`un_flow_action_log`、`un_flow_biz_binding`、`un_flow_definition`、`un_flow_definition_version`、`un_flow_form_template`、`un_flow_instance`、`un_flow_instance_trace`、`un_flow_instance_variable`、`un_flow_node_template`。这些表名禁止直接纳入新 DB 设计。

可参考 flow 表边界：

- 模板与版本：`un_flow_temp`、`un_flow_temp_ver`、`un_flow_temp_ver_node`、`un_flow_temp_ver_line`、`un_flow_temp_ver_line_cond`、`un_flow_temp_ver_setting`、`un_flow_temp_ver_node_setting`。
- 模板部件与绑定：`un_flow_node_temp`、`un_flow_form_temp`、`un_flow_binding`。
- 运行实例记录：`un_flow_record`、`un_flow_record_node`、`un_flow_record_line`、`un_flow_record_line_cond`、`un_flow_record_setting`、`un_flow_record_node_setting`、`un_flow_record_var`。
- 任务与日志：`un_flow_task`、`un_flow_task_actor`、`un_flow_log_action`、`un_flow_log_trace`。

禁止直接沿用的 flow 实体/表名及原因：

| 禁止直接沿用表名 | 与 DDL 确认表的关系 | 禁止原因 |
| --- | --- | --- |
| `un_flow_definition`、`un_flow_definition_version` | 概念上接近 `un_flow_temp`、`un_flow_temp_ver` | 实体存在但 Flyway 未建表，不能作为 DBA 设计输入的事实来源 |
| `un_flow_instance`、`un_flow_instance_trace`、`un_flow_instance_variable` | 概念上接近 `un_flow_record`、`un_flow_log_trace`、`un_flow_record_var` | 旧项目同时出现 record/instance 两套语义，新库必须二选一并重设字段，不得双轨并存 |
| `un_flow_action_log` | 概念上接近 `un_flow_log_action` | 旧实体表名与 DDL 表名反向命名，不能复制造成日志表重复 |
| `un_flow_biz_binding` | 概念上接近 `un_flow_binding` | 旧实体未建表，绑定关系应按新 PRD 的模块/动作/流程版本重新设计 |
| `un_flow_form_template`、`un_flow_node_template` | 概念上接近 `un_flow_form_temp`、`un_flow_node_temp` | 旧实体未建表；如新库采用完整 `template` 命名，也必须作为新设计，不得宣称沿用旧实体 |

DBA 设计原则：

- flow 新表可以参考旧 DDL 的领域拆分，但必须按 PRD 重新确认“模板/定义、版本、实例/记录、任务、日志、绑定、变量”的统一术语。
- 不得从 `examine-flow/entity/po` 全量反推建表，不得把未被 Flyway DDL 确认的实体作为 `sql/init.sql` 表清单。
- 如选择把旧缩写 `temp` 改为 `template`、把 `record` 改为 `instance`，必须在 `docs/db_design.md` 中给出完整映射、索引和迁移说明，不能同时保留旧缩写表和新完整表。
- 新 flow 设计需要补充发布完整性检查、审批原因、业务状态联动、任务幂等、并发防重复和历史快照字段；旧 DDL 只提供参考，不满足直接上线标准。

## 可参考的实体、接口、页面、配置或脚本

### 可参考实体

| 场景 | 实体路径 | 支撑场景 | 注意事项 |
| --- | --- | --- | --- |
| 平台账号 | `examine-plat/src/main/java/com/unique/examine/plat/entity/po/PlatAccount.java` | 登录、平台账号、当前用户 | 新项目不能直接返回密码 hash，需 VO 脱敏 |
| 系统与租户 | `PlatSystem.java`、`PlatTenant.java` | 创建系统、系统启停、多租户 | 需补系统编码、域名入口、停用访问规则、租户切换 |
| 平台 RBAC | `PlatMenu.java`、`PlatRole.java`、`PlatRoleMenu.java`、`PlatAccountRole.java` | 平台菜单权限 | 需补权限预览、按钮/操作权限和缓存刷新 |
| 应用模块 | `ModuleApp.java`、`ModuleModel.java`、`ModuleField.java` | 应用配置、模块建模、字段设计 | 需补草稿/发布/版本/回滚 |
| 动态记录 | `ModuleRecord.java`、`ModuleRecordData.java`、`ModuleRecordHistory.java` | 运行台数据、EAV、历史 | 需补 typed index、唯一性、字段删除历史展示 |
| 权限和成员 | `ModuleMember.java`、`ModuleDept.java`、`ModuleRole.java`、`ModuleRole*Perm.java` | 系统内成员、部门、角色、菜单/字段/操作权限 | 旧字段权限未形成完整运行校验，需要重构 |
| 导出 | `ModuleExportTpl.java`、`ModuleExportTplField.java`、`ModuleExportJob.java` | 导出模板和任务 | 需补脱敏、任务重试、导入、任务明细 |
| 流程 | `FlowTemp.java`、`FlowTempVer.java`、`FlowRecord.java`、`FlowTask.java`、`FlowTaskActor.java`、`FlowLogAction.java` | 流程模板、实例、任务、日志 | 需核对迁移表，补发布检查和状态联动 |
| 上传 | `UploadFile.java`、`UploadFilePart.java`、`UploadStorageConfig.java` | 文件中心、附件字段、导出结果 | 需补引用关系、对象存储、分片合并、安全 |
| OpenAPI | `AppClient.java`、`AppClientCredential.java`、`AppClientScope.java`、`AppIpWhitelist.java`、`AppAccessLog.java` | 外部系统接入、凭证、scope、日志 | 需补授权执行、限流、IP 白名单和日志落点 |

### 可参考接口

| 接口区域 | 旧路径 | 支撑场景 | 规避点 |
| --- | --- | --- | --- |
| 平台认证 | `examine-web/.../controller/AuthController.java`，`/v1/platform/auth` | 注册、登录、刷新、退出、当前用户 | 错误码、登录失败限制、找回密码、验证码需补齐 |
| 平台 OpenAPI 客户端 | `PlatformAppController.java`，`/v1/platform/apps` | 创建 client、凭证轮换、启停 | 需要拆平台/系统 OpenAPI 授权范围 |
| 系统态模块元数据 | `SystemModuleMetaController.java`，`/v1/system/module/meta` | 应用、模型、字段、关系、动作 | 新 API 不应直接返回 PO，需 BO/VO |
| 系统态记录 | `SystemModuleRecordController.java`，`/v1/system/records` | 记录新增、详情、更新、删除、DSL 查询、关联查询 | 需要权限、字段校验、唯一性和事务边界明确 |
| 系统态 RBAC | `SystemModuleRbacController.java`、`SystemModuleAuthController.java` | 角色、菜单、成员、权限预览、运行菜单 | 字段权限和按钮禁用态需补 |
| 系统态流程 | `SystemFlowController.java`、`SystemFlowInboxController.java`、`SystemFlowQueryController.java` | 发起审批、办理、待办、实例查询 | 需要审批原因强制、状态联动、并发幂等 |
| 系统态上传 | `SystemUploadController.java`，`/v1/system/uploads` | 上传、列表、详情、预览、下载、删除 | 需要引用关系、对象存储、权限和安全 |
| 系统态导出 | `SystemModuleExportController.java`、`SystemModuleExportJobController.java` | 导出模板、同步导出、异步任务 | 需要脱敏、导入、复杂模板、任务重试 |
| OpenAPI 记录 | `OpenApiModuleRecordController.java`，`/v1/open/records` | 外部创建/更新/查询记录 | 需要 scope、限流、审计、字段授权 |
| OpenAPI 流程 | `OpenApiFlowController.java`，`/v1/open/flow` | 外部发起和处理流程 | 需要业务幂等、外部用户映射、授权范围 |

### 页面参考

旧项目未发现独立前端页面源码。可参考的“页面能力”仅来自后端配置对象：

- `ModulePage`
- `ModulePageBlock`
- `ModuleMenu`
- `ModuleListView`
- `ModuleListViewCol`
- `ModuleListFilterTpl`
- `ModuleListFilterField`

这些对象可以支撑列表页、表单页、详情页、菜单和保存视图的配置模型，但不能替代新前端页面设计。新项目前端需要从 PRD 和冻结 API 重新实现平台中心、配置中心、运行台、流程工作台和运维中心。

### 可参考配置

- `examine-web/src/main/resources/application.yml`
  - 默认端口 `9999`
  - datasource、Redis 环境变量配置
  - Flyway `classpath:db/migration`
  - MyBatis-Plus mapper locations
  - 日志文件、Actuator health/info
  - `examine.session.store`
  - `examine.openapi.signing-master-key`
  - `examine.upload.local-root-path`
  - 上传大小、扩展名、content-type 白名单

- `examine-web/src/main/resources/application-prod.yml`
  - 生产环境配置模板，可作为新项目环境化配置参考。

### 可参考脚本

本次扫描未发现 `.sh`、`.bat`、`.cmd`、`.ps1`、Dockerfile 或 docker-compose 文件。部署脚本、启停脚本、配置模板和构建说明需要在新项目中重新补齐。

## 不建议沿用的问题

- 不建议沿用旧生成式 Controller 对外暴露。旧模块中大量 Controller 使用 `/queryById/{id}`、`/add`、`/update`、`/queryPageList`、`/deleteByIds`，更像基础 CRUD 调试接口。
- 不建议直接暴露 PO 实体作为 API 入参出参。新项目需要 SaveBO、UpdateBO、DTO、VO 分层。
- 不建议继续使用散落的数字错误码和字符串异常。应按模块错误码枚举输出前端可理解提示和日志定位信息。
- 不建议依赖 `SchemaCompatibilityRepair` 运行时补表补列。新项目应以初始化 SQL 和 migration 为准。
- 不建议把平台 OpenAPI 客户端和系统内 OpenAPI 授权混为一谈。PRD 和 DB 设计需明确边界。
- 不建议把所有权限压缩到菜单/API 权限。需求要求字段、操作、数据范围、OpenAPI、导出脱敏等更细粒度权限。
- 不建议只用 EAV `value_text` 承担所有查询。旧项目已补 typed columns 和索引，说明性能风险明显，新设计需继续强化。
- 不建议让流程设计器只是 JSON 编辑器。旧 `graphJson` 能支撑引擎，但前端必须可视化，后端必须结构化校验。
- 不建议让导出和文件上传成为同步阻塞大操作。导出任务需要状态、失败原因、重试、结果文件和审计。
- 不建议保留乱码中文文案。旧项目注释、Swagger、异常提示存在编码异常，新项目所有文档、SQL 注释、接口说明应统一 UTF-8。
- 不建议让 `examine-web` 堆业务实现。新项目应把业务能力放回各模块，Web 只做入口和装配。

## 与新项目实现相关的路径索引

### Maven 与启动

- 父 POM：`.codex/oldexamine/pom.xml`
- 启动模块 POM：`.codex/oldexamine/examine-web/pom.xml`
- 启动类：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/ExamineWebApplication.java`
- 应用配置：`.codex/oldexamine/examine-web/src/main/resources/application.yml`
- 生产配置：`.codex/oldexamine/examine-web/src/main/resources/application-prod.yml`

### Flyway 与 SQL

- 迁移目录：`.codex/oldexamine/examine-web/src/main/resources/db/migration`
- 平台 DDL：`.codex/oldexamine/examine-web/src/main/resources/db/migration/V1__platform_ddl.sql`
- 平台 RBAC DDL：`.codex/oldexamine/examine-web/src/main/resources/db/migration/V11__plat_rbac_ddl.sql`
- 上传 DDL：`.codex/oldexamine/examine-web/src/main/resources/db/migration/V3__upload_ddl.sql`
- 模块 DDL：`.codex/oldexamine/examine-web/src/main/resources/db/migration/V5__module_ddl.sql`
- 流程 DDL：`.codex/oldexamine/examine-web/src/main/resources/db/migration/V7__flow_ddl.sql`
- OpenAPI DDL：`.codex/oldexamine/examine-web/src/main/resources/db/migration/V9__app_ddl.sql`
- Flyway 启动策略：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/config/FlywayStartupConfig.java`
- Schema 修复反例：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/flyway/SchemaCompatibilityRepair.java`
- 手工标记反例：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/flyway/FlywayManualMark.java`

### 平台

- 认证 Controller：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/controller/AuthController.java`
- 平台认证服务：`.codex/oldexamine/examine-plat/src/main/java/com/unique/examine/plat/manage/PlatAuthManageService.java`
- 平台 RBAC 服务：`.codex/oldexamine/examine-plat/src/main/java/com/unique/examine/plat/manage/PlatRbacManageService.java`
- 平台权限服务：`.codex/oldexamine/examine-plat/src/main/java/com/unique/examine/plat/manage/PlatPermissionManageService.java`
- 平台账号实体：`.codex/oldexamine/examine-plat/src/main/java/com/unique/examine/plat/entity/po/PlatAccount.java`
- 系统实体：`.codex/oldexamine/examine-plat/src/main/java/com/unique/examine/plat/entity/po/PlatSystem.java`
- 租户实体：`.codex/oldexamine/examine-plat/src/main/java/com/unique/examine/plat/entity/po/PlatTenant.java`

### 动态模块

- 元数据 Controller：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/controller/SystemModuleMetaController.java`
- 记录 Controller：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/controller/SystemModuleRecordController.java`
- 权限 Controller：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/controller/SystemModuleRbacController.java`
- 模块元数据服务：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/manage/SystemModuleMetaService.java`
- 记录门面服务：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/manage/ModuleRecordFacadeService.java`
- 字段类型注册：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/field/ModuleFieldTypeRegistry.java`
- EAV 查询 DTO：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/entity/dto/ModuleRecordDslQuery.java`
- 自动编号服务：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/manage/ModuleSerialNoService.java`
- 数据范围服务：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/manage/ModuleDataScopeService.java`
- 模块权限缓存服务：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/manage/ModuleAuthService.java`

### 流程

- 流程操作 Controller：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/controller/SystemFlowController.java`
- 流程待办 Controller：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/controller/SystemFlowInboxController.java`
- 流程查询 Controller：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/controller/SystemFlowQueryController.java`
- 流程引擎：`.codex/oldexamine/examine-flow/src/main/java/com/unique/examine/flow/manage/FlowEngineService.java`
- 流程设计图服务：`.codex/oldexamine/examine-flow/src/main/java/com/unique/examine/flow/manage/FlowTempVerGraphService.java`
- 待办服务：`.codex/oldexamine/examine-flow/src/main/java/com/unique/examine/flow/manage/FlowTaskInboxService.java`
- 业务操作服务：`.codex/oldexamine/examine-flow/src/main/java/com/unique/examine/flow/manage/FlowBizActionService.java`

### 文件与导出

- 上传 Controller：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/controller/SystemUploadController.java`
- 文件实体：`.codex/oldexamine/examine-upload/src/main/java/com/unique/examine/upload/entity/po/UploadFile.java`
- 存储配置实体：`.codex/oldexamine/examine-upload/src/main/java/com/unique/examine/upload/entity/po/UploadStorageConfig.java`
- 导出 Controller：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/controller/module/SystemModuleExportController.java`
- 导出任务 Controller：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/controller/module/SystemModuleExportJobController.java`
- 导出服务：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/manage/SystemModuleExportService.java`
- 导出任务服务：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/manage/SystemModuleExportJobService.java`
- 导出任务 Runner：`.codex/oldexamine/examine-module/src/main/java/com/unique/examine/module/manage/SystemModuleExportJobRunner.java`

### OpenAPI

- 平台 OpenAPI client Controller：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/controller/PlatformAppController.java`
- OpenAPI 记录 Controller：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/controller/OpenApiModuleRecordController.java`
- OpenAPI 流程 Controller：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/controller/OpenApiFlowController.java`
- OpenAPI 鉴权过滤器：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/security/OpenApiAuthenticationFilter.java`
- OpenAPI 幂等服务：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/service/OpenApiIdempotencyService.java`
- 签名支持：`.codex/oldexamine/examine-app/src/main/java/com/unique/examine/app/security/OpenApiSignatureSupport.java`
- 密钥加密：`.codex/oldexamine/examine-app/src/main/java/com/unique/examine/app/security/OpenApiSigningSecretCrypto.java`

### Web 安全与日志

- Token 过滤器：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/security/TokenAuthenticationFilter.java`
- 系统上下文拦截器：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/security/SystemContextInterceptor.java`
- 模块权限拦截器：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/security/ModuleApiPathPermissionInterceptor.java`
- 全局异常：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/handler/GlobalExceptionHandler.java`
- requestId/filter：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/security/RequestContextFilter.java`
- 请求摘要日志：`.codex/oldexamine/examine-web/src/main/java/com/unique/examine/web/logging/RequestSummaryInterceptor.java`

## 推荐沿用与禁止简单照搬

### 推荐沿用

- Maven 多模块结构和 `examine-web` 启动模块模式。
- 表按模块前缀分组，平台前缀固定 `un_plat_`。
- SQL/Flyway 按平台、上传、模块、流程、OpenAPI 分批组织的思路。
- `systemId`、`tenantId`、`appId`、`modelId` 贯穿动态业务数据的隔离思想。
- 动态模块的应用、模型、字段、字段值、记录历史、列表视图、菜单、权限、导出模板等领域对象。
- 流程模板版本、流程记录、任务、变量、日志、设计图结构化存储的基本方向。
- OpenAPI 凭证轮换、签名、幂等和调用日志方向。
- 文件元数据表、存储配置表、本地存储扩展到对象存储的方向。

### 禁止简单照搬

- 旧生成式 Controller 和接口路径。
- 旧 PO 直接作为接口入参出参。
- 旧乱码注释、Swagger、异常提示。
- 旧运行时 schema repair 和 Flyway 手工标记策略。
- 旧错误码混用和字符串异常。
- 旧 `examine-web` 堆业务聚合的结构。
- 旧流程 JSON 编辑式体验。
- 旧只按创建人范围过滤的数据权限。
- 旧未完整落地的实体表映射。
- 旧简易 xlsx 生成方式作为打印或复杂导出的最终方案。
