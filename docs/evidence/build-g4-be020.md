# TASK-BE-020 G4 后端自检证据

## 实现概要

- Worker：G4 后端 worker
- 任务：TASK-BE-020 模块配置 APIs
- 范围：只修改 `backend/examine-module/**`，新增本证据文件。
- 实现方式：沿用 G3 contract-first 风格，新增 `ModuleConfigController`、`ModuleConfigService`、`ModuleConfigModels`，以确定性内存样例返回冻结契约需要的配置元数据。
- 依赖调整：`backend/examine-module/pom.xml` 增加 `spring-boot-starter-web`，用于 Controller 注解编译。
- 未实现内容：未实现动态运行态记录 CRUD、导入导出执行、附件、序号分配、流程引擎、Agent 写入。

## 覆盖能力

- 模块组：运行态顶部分组、排序、可见角色、发布版本。
- 模块：模块基本信息、导航元数据、行详情目标、发布状态。
- 字段：字段类型、存储类型、筛选操作符、排序、必填、脱敏、导入导出规则、读写权限元数据。
- 字典：字典类型、状态/标签语义、颜色、图标、默认项、停用原因、看板可用性。
- 场景和列表 schema：列、筛选、排序、分页、空态、权限快照、行详情目标。
- 页面动作：工具栏动作、批量动作、选择规则、幂等要求、同步/异步结果契约。
- 权限绑定：模块级、动作级、字段级权限和数据范围表达式。
- 发布检查：失败项、警告项、影响范围、traceId。
- 导入导出配置：模板、字段映射、去重策略、格式、预检要求；执行明确由 TASK-BE-022 承接。
- 打印模板：模板编码、版本、可见角色、绑定字段、预览文件、发布状态。

## 端点列表

- `GET /api/v1/systems/{systemId}/module-groups`
- `POST /api/v1/systems/{systemId}/module-groups`
- `PATCH /api/v1/systems/{systemId}/module-groups/{groupId}`
- `POST /api/v1/systems/{systemId}/module-groups/{groupId}/publish`
- `GET /api/v1/systems/{systemId}/modules`
- `POST /api/v1/systems/{systemId}/modules`
- `GET /api/v1/systems/{systemId}/modules/{moduleId}`
- `PATCH /api/v1/systems/{systemId}/modules/{moduleId}`
- `GET /api/v1/systems/{systemId}/modules/{moduleId}/fields`
- `POST /api/v1/systems/{systemId}/modules/{moduleId}/fields`
- `PATCH /api/v1/systems/{systemId}/modules/{moduleId}/fields/{fieldId}`
- `GET /api/v1/systems/{systemId}/dict-types`
- `POST /api/v1/systems/{systemId}/dict-types`
- `GET /api/v1/systems/{systemId}/dict-types/{dictTypeId}/items`
- `POST /api/v1/systems/{systemId}/dict-types/{dictTypeId}/items`
- `GET /api/v1/systems/{systemId}/modules/{moduleId}/scenes`
- `POST /api/v1/systems/{systemId}/modules/{moduleId}/scenes`
- `GET /api/v1/systems/{systemId}/modules/{moduleId}/list-schema`
- `GET /api/v1/systems/{systemId}/modules/{moduleId}/actions`
- `POST /api/v1/systems/{systemId}/modules/{moduleId}/actions`
- `GET /api/v1/systems/{systemId}/modules/{moduleId}/permissions`
- `GET /api/v1/systems/{systemId}/modules/{moduleId}/import-export-config`
- `POST /api/v1/systems/{systemId}/modules/{moduleId}/import-export-config`
- `GET /api/v1/systems/{systemId}/modules/{moduleId}/print-templates`
- `POST /api/v1/systems/{systemId}/modules/{moduleId}/print-templates`
- `POST /api/v1/systems/{systemId}/modules/{moduleId}/publish-check`
- `POST /api/v1/systems/{systemId}/modules/{moduleId}/publish`
- `POST /api/v1/systems/{systemId}/modules/{moduleId}/rollback`

## 自检命令

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend/pom.xml -pl examine-module -am -DskipTests compile
```

结果：PASS。

关键输出：

```text
[INFO] examine ............................................ SUCCESS
[INFO] examine-core ....................................... SUCCESS
[INFO] examine-module ..................................... SUCCESS
[INFO] BUILD SUCCESS
```

## diff 检查

命令：

```powershell
git diff --check
```

结果：PASS，无空白错误。仅输出工作区既有 tracked 文件的 LF/CRLF warning：

```text
.cursor/session/issues/registry.jsonl
docs/design/pre-coding-readiness.md
docs/design/user-approval.md
```

## 残余风险

- 当前为 contract-first 确定性样例响应，尚未接数据库持久化和真实权限服务。
- 导入导出只提供配置元数据，实际执行、任务结果和文件生成归属 TASK-BE-022。
- 发布检查当前返回可通过样例，后续接入真实字段索引、权限快照、运行态列表缓存后需要补充失败项校验。
- 工作区中 `backend/`、`docs/api/`、`docs/evidence/` 等目录在本轮开始前已处于未跟踪状态，本轮仅在声明范围内新增/修改文件。
