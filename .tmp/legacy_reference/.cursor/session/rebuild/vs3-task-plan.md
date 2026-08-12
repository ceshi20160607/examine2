# VS3 无代码配置发布任务计划

## 1. Meta

- node: `S3-VS3-CONFIG-PUBLISH`
- linked_design: `DESIGN-EXAMINE2-V1` version `1.0`
- owner: `planner`
- controller: `pm`
- verifier: `leader`
- status: `accepted`
- contract_status: `FROZEN`
- updated_at: `2026-07-15T12:25:05+08:00`

## 2. 业务完成定义

系统 Owner/Admin 无需编写代码或原始 JSON，即可创建模块组和模块、配置字段/字典/列表/表单/详情页面、动作和结构化规则；修改进入共享草稿，普通成员继续读取旧发布版。管理员检查并修复阻塞项后发布系统级不可变配置快照，查看版本差异并把历史快照回滚为新版本。普通成员只看到其权限允许的已发布模块组/模块和 active version，直接访问无权限或未发布模块被服务端拒绝。

覆盖 `JRN-C6` 的配置核心、`JRN-C10`、`JRN-PUB1` 和 `JRN-B2`；关联 `REQ-MODULE-GROUP-001, REQ-MODULE-001, REQ-FIELD-001, REQ-DICT-001, REQ-PAGE-001, REQ-MENU-001, REQ-RULE-001, REQ-CONFIG-001, REQ-RBAC-001, REQ-MIGRATION-001`。

`JRN-C6` 的导入/导出/打印运行能力归 VS5，仪表盘页归 VS8；VS3 不发布无承接按钮，不把这些子旅程记为完成。VS4 才实现动态业务记录；VS3 的运行结果止于真实已发布导航、定义读回和 schema 驱动空状态。

## 3. 工作图

| task | depends_on | owner | write scope | verification | status |
|---|---|---|---|---|---|
| VS3-001 Contract review | VS2 accepted | product/uiux/architect/dba/test/pm | 本计划、review | 独立意见、冲突、状态/schema/API/UX/Gate freeze | DONE |
| VS3-002 Schema/generator/module shell | VS3-001 | dba/backend | `V3*`, `examine-module` base, reactor/wiring | empty + V1/V2 upgrade, constraints, delete/regenerate, ArchUnit | DONE |
| VS3-003 Draft aggregate | VS3-002 | backend/test | normalized config manage/API | group/module/dictionary/field/page/action/rule CRUD, revision/CAS/reference | DONE |
| VS3-004 Check/publish/rollback | VS3-003 | backend/test/ops | check/version/publish services | stale check, atomic publish, immutable snapshot, V1→V2→rollback V3 | DONE |
| VS3-005 Permission/runtime navigation | VS3-004 | backend/plat/test | core facade, module runtime API | dynamic permission registration, owner grant, epoch stale, filtered navigation | DONE |
| VS3-006 Configuration frontend | VS3-003..005 API | frontend/uiux | system admin studio/runtime shell | desktop designer, member preview, mobile read-only/navigation | DONE |
| VS3-007 Evidence/hardening | VS3-002..006 | test/ops | tests/evidence | build, integration, migration, concurrency, restart, E2E | DONE |
| VS3-008 Gate | VS3-007 | pm/all/leader | meeting/state/node | no open P0/P1; Leader verdict | DONE |

字段/页面设计器可以在 API 合同冻结后并行；`examine-module`、core 权限端口和 plat 实现的公共文件必须串行合并。没有发布前后双会话读回、数据库版本事实和权限负例不能验收配置页面。

## 4. 冻结发布合同

### 4.1 发布单元与状态

- 一个系统只有一个共享配置草稿和一个 active version；配置是系统级，不按租户分叉。
- 任一资源变更提升 `draft_revision`，草稿状态为 `CLEAN/DIRTY/CHECKING/CHECK_FAILED/CHECKED/PUBLISHING`。
- 检查记录为 `RUNNING/PASSED/FAILED/STALE`，绑定 `systemId + baseVersionId + draftRevision + draftChecksum`。草稿再变更立即使旧检查失效。
- 发布版本不可变，是否 active 只由配置根指针决定；checksum 允许重复，因为回滚可产生内容相同的新版本。
- 资源期望状态为 `ENABLED/DISABLED/ARCHIVED`。停用/归档是草稿变更，发布后生效；平台的系统紧急停用仍走 VS2 生命周期。
- 回滚只允许草稿 `CLEAN`，复制目标历史快照、重新检查并生成 N+1 发布版本，同时把规范化草稿重置到目标内容；不修改旧版本，不承诺逆转业务数据。

### 4.2 发布事务

发布命令锁定配置根并校验 revision/base/check/checksum，在一个事务中创建完整 canonical JSON snapshot、版本和发布记录，通过公开端口注册动态权限并授予内置 owner，CAS 更新 active version，提升 authz epoch，写 operation audit 和 outbox。任一步失败全部回滚，运行端继续读取旧 active version。

缓存键必须含 `systemId + activeVersionId`；提交后 outbox 负责主动失效，失效失败时运行 API 仍按根指针回源数据库，不能读取未发布草稿。

VS3 检查同步执行并持久化报告，单次配置 snapshot 限制 `2 MiB`、检查服务目标 `10s`；超限或超时返回明确错误。未来数据迁移检查可扩展为 job，但 VS3 不创建假队列。

## 5. 冻结数据合同

owner module 为 `examine-module`，表前缀 `un_module_*`：

- 根/发布：`config_root`, `config_check`, `config_check_issue`, `config_version`, `publish_record`。
- 规范化草稿：`group`, `definition`, `field`, `dictionary`, `dictionary_item`, `dictionary_item_closure`, `page`, `page_component`, `action`, `rule`, `config_reference`, `permission`。
- 运行导航不保存第二套可变菜单事实，由发布快照中的模块组、模块、默认页面和权限键编译得到；系统后台管理菜单仍为静态受权路由。

约束：

- 模块编码系统内唯一；字段/页面/动作/规则编码模块内唯一；字典编码系统内唯一；字典项编码字典内唯一。编码一旦发布不可修改或复用。
- 内部引用带 `system_id` 组合 FK，删除 `RESTRICT`；字典 closure 防环；模块组只是导航组织，不是业务数据父级。
- 页面组件和字段类型专属属性使用有 schema、大小和深度限制的 JSON；禁止任意脚本、SQL 和无边界整包 draft JSON。
- `config_reference` 是字段、页面、规则、动作、字典和模块依赖的可查询事实；保存时由结构化解析器同步维护，发布检查不依赖字符串扫描。
- `un_module_permission` 只保存资源到权限键的映射；授权唯一事实仍是 `un_plat_permission/role_permission/authz_epoch`。

## 6. 冻结配置语义

- 模块创建时自动生成默认 `LIST/FORM/DETAIL` 页面草稿；VS3 不创建仪表盘和自定义页面。
- 字段类型目录覆盖需求声明的基础、高级和系统字段；每类属性由后端 registry/schema 校验，前端显示类型专属面板。高级类型在 VS3 可配置和检查，运行值、公式、汇总、AI 执行分别在后续切片实现。
- 字典支持普通、树形、级联、状态、标签和字段选项；停用项不能成为新默认，历史显示快照由 VS4 记录模型承担。
- 规则使用最大深度 `5` 的结构化 AND/OR AST。叶子操作符按字段类型限制为 `EQ/NE/GT/GTE/LT/LTE/IN/NOT_IN/EMPTY/NOT_EMPTY/CONTAINS/BETWEEN`；效果限 `VISIBLE/REQUIRED/READ_ONLY/ACTION_ENABLED/DELETE_ALLOWED/APPROVAL_REQUIRED`。
- 权限显式 DENY 优先；字段不可见优先于只读/必填，冲突的隐藏必填规则阻断发布；动作必须同时满足权限和启用条件。
- 新模块动态权限至少生成 `module.{code}.view/create/update/delete` 及声明动作/字段权限。新权限默认只授予内置 `system_owner`，普通角色默认拒绝；模块组可见性由至少一个可见模块推导，不保存 `visibleRoleIds`。

## 7. 冻结 API 合同

| zone | API group | 结果 |
|---|---|---|
| admin root | `/api/v1/systems/{systemId}/admin/config` | 草稿状态、revision、active/base version、检查摘要 |
| admin resources | `.../config/module-groups|modules|dictionaries` | 规范化配置和排序/影响检查 |
| module resources | `.../config/modules/{moduleId}/fields|pages|actions|rules` | 类型化配置、引用和版本 CAS |
| publication | `POST .../config/checks`, `POST .../config:publish` | 持久化检查报告和原子发布 |
| history | `GET .../config/versions`, `GET .../config/versions/{a}:diff/{b}`, `POST .../config/versions/{id}:rollback` | 历史、差异、N+1 回滚 |
| preview | `GET .../config/preview?memberId=...` | 以目标成员有效权限渲染发布/草稿差异，管理员身份不冒充成员 |
| runtime | `/api/v1/systems/{systemId}/runtime/navigation`, `.../runtime/modules/{moduleCode}/definition` | 只读 active snapshot 并按当前 permission snapshot 过滤 |

所有 ID 以 string 传输；mutation 使用 CSRF、服务端 context、权限、`Idempotency-Key` 和 `If-Match/version` 或 `draftRevision`。错误至少冻结 `CONFIG_CHECK_FAILED`, `CONFIG_CHECK_STALE`, `CONFIG_DRAFT_DIRTY`, `CONFIG_VERSION_CONFLICT`, `CONFIG_REFERENCE_INVALID`, `CONFIG_PUBLISH_CONFLICT`, `MODULE_NOT_PUBLISHED`, `RESOURCE_NOT_FOUND`, `PERMISSION_DENIED`。

## 8. UI 与移动 Gate

- 系统后台提供模块组树/模块列表、模块工作区、字段设计器、列表/表单/详情页面设计器、动作规则、权限预览、发布检查和版本历史。
- 字段/页面设计器使用资源区、中央预览和属性面板；管理员不编辑原始 JSON。检查问题按资源定位，发布成功必须显示 active version 并可进入运行态读回。
- `<768px` 只读状态、检查结果和版本历史；`768-1199px` 允许列表与基本属性但不开放三栏设计；`>=1200px` 开放完整设计器。
- B2 在桌面和移动均可用：只显示有权限且至少含一个可见模块的组；移动使用组选择器和模块抽屉/列表，不保留固定左栏。

## 9. 证据目标

- V3 空库与 V1+V2 升级、MySQL 8.0/8.4 约束负例、删除重生成和模块边界检查。
- 草稿不污染运行、发布重启读回、V1→V2→回滚 V3、过期检查、双发布、幂等异体和发布故障原子回滚。
- 动态权限注册、owner 默认可见、普通成员默认拒绝、角色授权后可见、发布提升 epoch、直接 URL 拒绝。
- desktop `1440x900`/`1280x720` 设计与发布旅程，mobile `390x844` 只读和 B2 导航，无横向溢出。
- `.cursor/session/evidence/vs3/*`、`.cursor/session/meetings/S3-vs3-review.md` 和 Leader acceptance。
