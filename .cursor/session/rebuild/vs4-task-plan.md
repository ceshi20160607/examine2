# VS4 动态业务运行任务计划

## 1. Meta

- node: `S3-VS4-DYNAMIC-RUNTIME`
- linked_requirements: `JRN-B3/B4/B5/B11`, `REQ-RUNTIME-001/002/003`, `REQ-DATA-001/002`, `REQ-DATA-MODEL-001`, `REQ-COLLAB-001`, `REQ-EFFICIENCY-001`, `REQ-RBAC-001`, `REQ-MOBILE-001`, `NFR-PERF-001`
- linked_design: `DESIGN-EXAMINE2-V1` version `1.0`
- owner: `planner`
- controller: `pm`
- verifier: `leader`
- status: `contract_review`
- contract_status: `DRAFT_REV6`
- started_at: `2026-07-15T12:36:48+08:00`
- updated_at: `2026-07-15T16:08:36+08:00`

## 2. 业务完成定义

普通系统成员从 VS3 已发布模块进入真实业务列表，能够按权限搜索、筛选、排序、分页、配置列和保存视图；创建、自动保存草稿、恢复草稿、提交、编辑、归档、移入回收站和恢复动态记录；执行批量编辑、转交、归档和移入回收站；在详情中使用关联、子表、团队、评论和历史；通过全局搜索、收藏、最近访问和快捷创建恢复重复工作。

保存不是生成 CRUD，也不是把整条记录塞进 JSON。运行时依据不可变的已发布 schema 版本，在一个事务中持久化主记录、类型化字段值、唯一事实、索引、搜索投影、关联、子表、团队、评论、历史、audit 和 outbox。任何一步失败必须全部回滚；服务端重新执行字段、动作和数据范围授权，不能信任客户端隐藏状态。

本节点只有在 B3/B4/B5/B11 的 VS4 子能力分别验收后才可通过，不能用一个列表页、一个通用表单或静态原型冒充旅程完成。整套系统仍需继续完成 VS5 及后续节点，VS4 通过不等于最终项目完成。

## 3. 切片边界与能力状态

| journey | VS4 必须完成 | 后续切片 | Gate 状态口径 |
|---|---|---|---|
| B3 列表工作台 | 搜索、筛选、排序、分页、列配置、保存视图、跨页显式选择、批量编辑/转交/归档/回收站、打开详情 | 导入/导出归 VS5 | `PARTIAL`，逐子能力记录，不写整旅程完成 |
| B4 创建编辑 | 动态表单、规则、校验、自动编号、唯一、自动/手动草稿、恢复/放弃、冲突恢复、提交读回 | 文件字段归 VS5，AI_FILL 归 VS10 | `PARTIAL` |
| B5 详情协作 | 基本信息、关联、子表、团队、评论、历史、前后记录、转交、编辑、归档/回收站/恢复 | 附件/打印归 VS5，审批历史归 VS6 | `PARTIAL` |
| B11 效率入口 | 命令中心、业务记录全局搜索、模块/记录收藏、最近访问、快捷创建、我的草稿 | 待办/任务/日志归 VS6，扫码/拍照归 VS5 | `PARTIAL` |

VS5/VS6/VS10 能力不只是在前端隐藏：VS3 发布检查、VS4 schema 激活检查和 record-schema 都必须使用同一能力矩阵。必填但不可运行的字段阻止 schema 激活，返回稳定原因 `FIELD_RUNTIME_UNAVAILABLE`；不可运行的动作不进入服务端可执行动作集合，也不进入前端 payload。

## 4. 工作图

| task | depends_on | owner | 必须产出 | verification | status |
|---|---|---|---|---|---|
| VS4-001 Contract review | VS3 accepted | product/uiux/architect/dba/backend/test/ops/pm | 本计划、三路独立意见、决策会议 | 数据、字段、动作、权限、API、UX、性能、故障、制品无开放 P0/P1 | IN_REVIEW |
| VS4-002 Schema/generator/index plan | VS4-001 frozen | architect/dba/backend | V4 migration、20 表、100 个 base 文件、索引/迁移计划 | MySQL 8.0/8.4 空库/升级/回滚、约束负例、两次精确重生成、EXPLAIN | BLOCKED |
| VS4-003 Record aggregate/write API | VS4-002 | backend/test | schema 投影、record/value/unique/index/search、草稿/生命周期/批量 API | 类型矩阵、CAS、幂等、全事务故障点、读回 | BLOCKED |
| VS4-004 Query/view/efficiency API | VS4-002..003 | backend/frontend/test | 固定 query AST、分页、saved view/favorite/recent/global search | 权限投影、稳定排序、限制、性能语料 | BLOCKED |
| VS4-005 Relations/subtables/collaboration/history | VS4-003 | backend/frontend/test | 关联、子表、团队、评论、历史 API/UI | 原子保存、重复竞争、目标权限、敏感遮罩 | BLOCKED |
| VS4-006 Runtime authorization | VS4-003..005 | architect/plat/module/test | core port、plat adapter、七类 scope、字段读写/敏感 | DENY、多角色、epoch、404 防存在性泄漏、module 零 plat 表查询 | BLOCKED |
| VS4-007 Runtime frontend | VS4-003..006 API | product/uiux/frontend | 模块工作台、详情、表单、协作、命令中心 | desktop/compact/mobile 完整旅程、键盘/焦点/axe/异常态 | BLOCKED |
| VS4-008 Evidence/hardening | VS4-002..007 | test/ops | 自动化、百万数据性能、并发/故障、制品部署证据 | 门槛全部满足且可复现，无控制台/网络/溢出问题 | BLOCKED |
| VS4-009 Gate | VS4-008 | pm/all/leader | review meeting、acceptance、state/node | 无开放 P0/P1；Leader 独立验收 | BLOCKED |

合同冻结前禁止创建 V4 表和动态记录实现。合同冻结后 schema/generator 先行；查询与 UI 可在写 API 稳定后并行，但各角色只能写入工作图规定的范围。PM 负责收敛跨角色问题，Leader 只验收业务结果和证据，不代替角色补交产出。

## 5. 不可变 Schema 与模块边界

1. VS3 当前配置行可因 active pointer 回滚而删除重建，不能成为运行记录的历史外键。每次发布成功后，VS4 建立不可变 runtime schema 投影；数据库 migration 始终 forward-only，业务回滚只切 active pointer。
2. `runtime_schema_module` 以 `(system_id, schema_version_id, module_snapshot_id)` 为历史归属，并保存稳定 `logical_module_id=source_module_id`；以 `(system_id, schema_version_id)` FK 到 `un_module_config_version(system_id,id)`。`runtime_schema_field` 必须携带 module_snapshot_id、历史 field_snapshot_id 和跨版本稳定 logical_field_id，并 FK 到同版本 runtime module。
3. `record` 以 `(system_id, tenant_id, record_id, schema_version_id, module_snapshot_id)` 为运行聚合身份并 FK 到 runtime module；`record_value` 同时 FK 到该完整 record 身份和 `(system_id,schema_version_id,module_snapshot_id,field_snapshot_id)`，从数据库层阻止跨模块字段注入。
4. runtime field 保存 `field_scope=RECORD/SUBTABLE_COLUMN`、field_type 和可空 parent_field_snapshot_id。投影 `SUBTABLE.columnFieldIds` 时，为每个 `(parent_source_field_id,column_source_field_id)` 生成唯一 SUBTABLE_COLUMN snapshot 和稳定 logical key `sub:{parentSourceId}:{columnSourceId}`；普通字段 key 为 `record:{sourceFieldId}`。禁止 SUBTABLE_COLUMN 再引用 SUBTABLE，激活时阻止嵌套子表。
5. runtime field 建可被 FK 引用的 discriminator unique key。sub value FK 固定携带 `parent_field_type=SUBTABLE,parent_scope=RECORD,column_scope=SUBTABLE_COLUMN`；relation source FK 固定携带 `source_field_type=RELATION,source_scope=RECORD`，并用 CHECK 锁死 discriminator，不能只靠应用层判断类型。
6. relation 的 source 和 target 都使用 system、tenant、schema/module snapshot、record 完整组合 FK；全部历史 runtime FK 使用 `ON DELETE RESTRICT`，不得级联删除 schema/value/history。
7. snapshot id 只标识该版本投影；值同时保存 logical key、field version、source schema version 和 value type，保证历史解释不依赖当前配置。
8. `examine-module` 只能调用 `examine-core` 中的 `RuntimeAuthorizationFacade`；其实现位于 `examine-plat`。module 代码、Mapper 和 SQL 禁止直接读取 `un_plat_*`。
9. 发布、active pointer 回滚和新版本激活必须检查字段类型迁移与 index/normalization generation。存在未完成的不兼容迁移、失败回填或必填不可运行能力时，激活被阻止。
10. 公式、规则、字段范围都使用结构化 AST；禁止脚本、表达式字符串拼接 SQL、反射调用和用户 SQL。
11. V4 升级任务为每个既有 active config version 建一次 immutable projection；checksum 相同则幂等跳过。存在 required deferred field/action 时该 module 标记 `RUNTIME_UNAVAILABLE` 并返回稳定原因，不创建记录入口；后续每次 VS3 publish 在同一 owner transaction 写完整 runtime projection 和 checksum。
12. 新 schema 的 index plan 必须把同 logical field 的所有 live 旧记录转换到新 index_generation/normalization_generation；READY 前不能切 active pointer。BACKFILLING 期间每个业务写事务先对 ACTIVE/candidate plan 行 `FOR SHARE`，同事务双写两代 index/search/unique，并写带 `recordId+recordVersion+candidateGenerationId` 的 `INDEX_GENERATION_DELTA` outbox；回填只用 `(record_id,source_record_version)` CAS，旧版本不得覆盖并发新值。切换事务对两代 plan 行 `FOR UPDATE`，阻止新写并等待在途写，重放 delta 到无缺口后执行全量 anti-join、逐 logical field checksum 和 unique 冲突扫描，全部通过才在同一事务把 candidate 置 ACTIVE、旧代置 SUPERSEDED 并切 active pointer；失败回滚本次未提交的 pointer/status，原 ACTIVE 代继续服务。已成功切换后的业务回滚必须创建一条从当前 active schema 指向目标 schema 的反向 migration，使用新的 candidate generation 完成 preview/backfill/校验/切换；禁止直接把 SUPERSEDED generation 重新标为 ACTIVE。当前查询按 logical IDs + ACTIVE generation，不按历史 snapshot；值/历史仍使用 snapshot FK。
13. `action_capability_json`、`rule_ast_json` 各最大 256 KiB、AST 深度 5/叶子 20；runtime schema 响应只读取 snapshot JSON 并校验 checksum，禁止 fallback 到当前 config 行。

## 6. 精确数据模型

V4 新增以下 20 张 `un_module_*` 表。现有 48 张表增加至 68 张；生成器每表五个 base 文件，新增 100 个后精确总数为 340。命名沿用现有合同：Entity 与 `I*Service` 不加模块前缀，Mapper/XML/ServiceImpl 使用 `Module*` 前缀；不得存在第二套无前缀 Mapper/XML/Impl 重复家族。

| # | table | 事实、关键约束与索引 |
|---|---|---|
| 1 | `un_module_runtime_schema_module` | 不可变发布模块投影；历史 snapshot key + 稳定 logical_module_id；FK config version；保存 source_module_id/code/title/status、bounded action_capability_json、rule_ast_json、checksum；运行服务不得回读当前配置行 |
| 2 | `un_module_runtime_schema_field` | 不可变字段投影；module snapshot、稳定 logical_field_id、field_scope/type discriminator、parent snapshot；版本/模块内 snapshot/code 唯一；保存 source id/type/mode/cardinality/schema/normalizer/operator/sensitivity/capability |
| 3 | `un_module_runtime_index_plan` | logical field 的 index_generation_id/normalization_generation_id、path snapshot 和回填水位；状态 `DRAFT/BACKFILLING/READY/FAILED/ACTIVE/SUPERSEDED`；ACTIVE 前必须 READY |
| 4 | `un_module_runtime_migration_plan` | 类型变更的兼容级别、preview 计数、转换规则和阻断原因；一个 source/target field version 只允许一个有效计划 |
| 5 | `un_module_runtime_migration_run` | 每次 backfill 的范围、水位、成功/失败计数、错误摘要、开始结束时间；失败可从确认水位恢复 |
| 6 | `un_module_record` | 完整 historical schema/module FK + stable logical_module_id；record_no/title/status/prior_status/owner/department/version/draft_expiry/deleted_at；查询 key `(system,tenant,logical_module,status,record_id)` |
| 7 | `un_module_record_value` | 完整 record FK + field snapshot FK，并保存 logical_field_id；field version/type/ordinal；typed/encrypted/hash/display 互斥；每 snapshot field+ordinal 唯一 |
| 8 | `un_module_record_index` | logical module/field + index_generation_id + normalization_generation_id + optional path_snapshot_id + value_kind；九 typed 列互斥，MONEY 另带 currency_code；每类索引固定 system/tenant/logical module/logical field/generation/status/value/record_id |
| 9 | `un_module_record_search` | 每 `record + logical_field_id + index_generation_id + token_ordinal + token` 一行；查询与 readable logical field 集合和 ACTIVE generation 相交；敏感字段永不进入此表 |
| 10 | `un_module_record_unique` | ACTIVE/ARCHIVED 的 logical_module_id + logical_field_id + normalization_generation_id + normalized_hash 唯一；草稿/过期/回收站不占用；跨 snapshot 仍竞争同 logical unique |
| 11 | `un_module_record_relation` | source/target 都使用完整 system/tenant/schema/module/record FK；source field 必须是 RELATION；source+field+target 去重，ordinal 唯一 |
| 12 | `un_module_sub_record` | parent 完整 record FK + SUBTABLE field FK、row_id、ordinal、version/status；父记录行锁下检查最多 200，ordinal 唯一 |
| 13 | `un_module_sub_value` | sub record + 父 SUBTABLE 下 SUBTABLE_COLUMN snapshot/discriminator 双 FK；类型化值结构与 record_value 同源 |
| 14 | `un_module_record_team` | member + `OWNER/COLLABORATOR/VIEWER/FOLLOWER`；活动成员去重；最多 200；同 system/tenant 成员 FK |
| 15 | `un_module_comment` | record、author、parent_comment、body、edited/deleted/resolved/pinned/version；父子必须同 record，回复深度只允许 1 |
| 16 | `un_module_record_history` | append-only action/actor/schema version/record version/结构化 diff/snapshot；敏感值只留 masked/hash 元数据 |
| 17 | `un_module_saved_view` | member/module/name/query AST/columns/sort/version；每成员每模块最多 100，名称活动唯一 |
| 18 | `un_module_favorite` | member + `MODULE/RECORD` + object id 唯一；最多 500；目标不可见时读取时过滤 |
| 19 | `un_module_recent` | member + object 唯一 upsert；最近成功打开时间；最多 50、保留 90 天 |
| 20 | `un_module_record_sequence` | system/tenant/logical_module_id/logical_field_id 下原子 next value/version；跨 schema snapshot 不重置，编号单调、不重复、允许间隙且永不复用 |

所有表使用 bigint/string id 但 API 一律传 string；包含 system/tenant、created/updated actor、时间和 version，append-only 表除外。全部跨表关系使用上述完整组合约束和 `ON DELETE RESTRICT`。comment 的 parent 使用 `(system_id, tenant_id, record_id, parent_comment_id)` 组合 FK。relation/subtable/team/comment 上限都在 `SELECT ... FOR UPDATE` 锁定父 record 后校验并依赖唯一 ordinal，禁止 `COUNT + INSERT` 竞态。JSON 只保存 immutable schema、bounded action/rule AST、历史证据或受界限对象，不作为当前记录查询事实。

V4 migration 必须支持：空库全量重放、V1/V2/V3.2 顺序升级、MySQL `8.0.44` image digest `sha256:9c3380eac945af0736031b200027f581925927c81e010056214a4bd6b6693714` 和 `8.4.10` image digest `sha256:c831a0f11348d402b43d77453e17d770be2eef356615a2823fe0f5a0d6c8b9af`。数据库固定 `utf8mb4/utf8mb4_0900_ai_ci` 和 `ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION`。migration forward-only；已产生运行记录后不得删除被引用 runtime schema，业务回滚只能在兼容性检查后切 active pointer。8.4 使用持久数据卷执行升级、进程重启和读回。

## 7. 49 类字段运行矩阵

机器合同为 `rebuild/vs4-field-contract.json` schema v2，其 49 行是代码、schema 激活、API、UI、migration registry 和参数化测试的共同输入；实现不得维护第二份类型 switch 清单。每行固定 mode、target slice、min/max cardinality、normalizer、fixture profile、operator policy、index strategy/physical typed column、unique policy 和 self converter；跨类型 conversion 使用逐边 migrationEdges。
`rebuild/vs4-field-contract.schema.json` 是字段合同自身的 Draft 2020-12 约束；`rebuild/vs4-db-contract.json` 逐表冻结 20 张表的 PK/UK/FK/CHECK/index；`rebuild/vs4-verification-contract.json` 冻结 scope、failpoint、性能、并发、MySQL digest、生成器精确数量和发布证据。三份机器合同必须由 `.cursor/scripts/validate-vs4-api-contract.mjs` 同时执行正例和破坏性反例，不能用文档存在代替校验通过。

公共规则：所有可写字段先做 JSON schema、类型、长度、格式、required、read-only、visibility 和字段 write 权限校验；所有列表操作只允许已发布 operator；多值以 ordinal 保序；空字符串按类型规范为 null 或有效空值；错误定位到稳定 field code。API 读结果附 `mode`, `writable`, `masked`, `operators`, `unavailableReason`。

| type | mode / storage / cardinality | normalization, index, operators | UI/API 与非法值 |
|---|---|---|---|
| TEXT | writable / string / 1 | trim+NFKC；eq,ne,contains,prefix,empty；可唯一 | 单行输入；超长/控制字符拒绝 |
| TEXTAREA | writable / text / 1 | 保留换行、NFKC；contains,empty；不可排序/唯一 | 多行；超长拒绝 |
| PHONE | writable / string / 1..10 | E.164 hash+display；eq,empty；unique 时每个规范化元素全局唯一 | 电话输入；不可解析/第 11 个拒绝 |
| EMAIL | writable / string / 1..10 | trim+lower domain；eq,prefix,empty；unique 时每个规范化元素全局唯一 | email 输入；非法地址/第 11 个拒绝 |
| URL | writable / string / 1 | canonical http/https；eq,prefix,empty | URL 输入；非允许 scheme 拒绝 |
| IDENTITY | sensitive-writable / encrypted+hash / 1 | AES-GCM encryption key version + HMAC key version；eq,empty；可唯一，不排序/全文 | 输入后默认掩码；无 `sensitive.read` 不返回明文，无 `sensitive.query` 不返回 operator；非法格式拒绝 |
| NUMBER | writable / decimal / 1 | scale/round 固定；eq,ne,gt,gte,lt,lte,between,empty；可唯一 | 数字控件；NaN/越界/scale 拒绝 |
| PERCENT | writable / decimal / 1 | 0..100 canonical；数值 operators | 百分比控件；越界拒绝 |
| MONEY | writable / decimal+currency schema / 1 | currency+minor unit；数值 operators；同币种排序 | 金额控件；币种/scale 不符拒绝 |
| DATE | writable / date / 1 | ISO date；eq,before,after,between,empty | 日期控件；非法日历值拒绝 |
| DATETIME | writable / datetime UTC / 1 | 输入时区转 UTC；date operators | 日期时间控件；缺失/非法时区拒绝 |
| DATE_RANGE | writable / date ordinal 0..1 / 2 | start/end；overlaps,contains,before,after,empty | 范围控件；start>end 拒绝 |
| TIME | writable / time / 1 | 秒精度；eq,before,after,between,empty | 时间控件；非法时间拒绝 |
| TIME_RANGE | writable / time ordinal 0..1 / 2 | start/end；overlaps,contains,empty | 范围控件；逆序且未声明跨日时拒绝 |
| RADIO | writable / option id / 1 | 发布字典 id；eq,ne,in,empty | 单选；不存在/停用选项拒绝 |
| MULTI_SELECT | writable / option id / 0..100 | 去重保序；hasAny,hasAll,notAny,empty | 多选；重复/非法选项/第 101 个拒绝 |
| CASCADE | writable / option path / 0..20 | closure path 验证；containsNode,leafEq,empty | 级联；断裂路径/第 21 级拒绝 |
| SWITCH | writable / boolean / 1 | true/false；eq,empty | 开关；非布尔拒绝 |
| MEMBER | writable / member id / 0..50 | 活动同租户成员；hasAny,hasAll,empty | 成员选择；越租户/不可见成员/第 51 个拒绝 |
| DEPARTMENT | writable / department id / 0..50 | 活动同系统部门；hasAny,inTree,empty | 部门选择；越系统/失效部门/第 51 个拒绝 |
| TENANT | writable / tenant id / 0..50 | 当前 system 可见租户；hasAny,empty | 租户选择；无权目标/第 51 个拒绝 |
| ATTACHMENT | deferred-vs5 / none | 无 index/operator | record-schema 不返回可写控件；required 阻止激活 |
| IMAGE | deferred-vs5 / none | 无 index/operator | 同 ATTACHMENT |
| FILE_GROUP | deferred-vs5 / none | 无 index/operator | 同 ATTACHMENT |
| AUTO_NUMBER | system-computed / record_no or string / 1 | sequence canonical；eq,prefix,empty；可唯一 | 激活时分配，只读；客户端提交拒绝 |
| RELATION | writable / relation table / 0..500 | target id 去重保序；hasAny,empty | 关系选择器；目标不可见/越租户/重复拒绝 |
| REFERENCE | derived-readonly / typed value / 1 | 来源字段规范；沿声明关系刷新；来源 operators | 只读；客户端提交拒绝，循环/不可达依赖阻止激活 |
| SUBTABLE | writable / sub tables / 0..200 | row id/version/ordinal；仅声明聚合 operator | 子表编辑器；超行数/重复 row/越权字段拒绝 |
| ADDRESS | writable / bounded JSON / 1 | country/admin code/postcode+display；eqRegion,prefix,empty | 结构化地址；未知 code/深度/大小超限拒绝 |
| GEO | writable / bounded JSON / 1 | WGS84 lat/lng；withinBox,near,empty，需计划索引 | 地理控件；坐标越界/精度超限拒绝 |
| RATING | writable / integer / 1 | schema min/max；数值 operators | 评分；非整数/越界拒绝 |
| PROGRESS | writable / decimal / 1 | 0..100；数值 operators | 进度控件；越界拒绝 |
| TAG | writable / string / 0..100 | trim+NFKC+case policy、去重；hasAny,hasAll,empty | 标签；空标签/重复/超长/第 101 个拒绝 |
| BARCODE | writable / string / 1 | symbology+payload；eq,prefix,empty；可唯一 | VS4 提供文本输入；扫码/拍照按钮延后 VS5；非法校验位拒绝 |
| SIGNATURE | deferred-vs5 / none | 无 index/operator | 无二进制承接，required 阻止激活 |
| RICH_TEXT | writable / sanitized text / 1 | allowlist HTML/JSON model；contains,empty | 富文本；脚本、危险 URL、超长拒绝并服务端净化 |
| JSON | writable / bounded JSON / 1 | schema canonical；仅声明 JSON path eq/exists，禁止任意 path | schema 驱动编辑；深度>10、>256KiB、schema 不符拒绝 |
| SECRET | sensitive-writable / encrypted+hash / 1 | AES-GCM+key version、HMAC；eq,empty；不排序/全文 | write-only 更新，读取只返回是否已设置和掩码；日志/历史不得明文 |
| STATUS | writable-with-transition / option id / 1 | 状态机合法边；eq,in,empty | 状态控件；越过未声明转换拒绝 |
| FORMULA | derived-readonly / typed value / 1 | 结构化 AST 计算并 materialize；结果类型 operators | 只读；脚本/SQL/循环/类型不合阻止激活 |
| SUMMARY | derived-readonly / typed value / 1 | 声明关系聚合；结果 operators | 只读；无关系/非法聚合阻止激活 |
| CALCULATED | derived-readonly / typed value / 1 | 结构化算术/日期 AST；结果 operators | 只读；除零/类型错误记录重算失败且不覆盖旧值 |
| LOOKUP | derived-readonly / typed multi-value / 0..100 | 声明关系+目标字段；按目标 ordinal；来源 operators | 只读；不可见目标值不泄漏；结果超限记录重算失败 |
| AGGREGATE | derived-readonly / decimal/integer / 1 | relation/subtable count/sum/min/max/avg；数值 operators | 只读；不支持类型阻止激活 |
| AI_FILL | deferred-vs10 / none | 无 index/operator | required 阻止激活，不返回假控件 |
| CREATED_BY | system-computed / member id / 1 | actor id；eq,in | 只读；客户端提交拒绝 |
| CREATED_AT | system-computed / datetime UTC / 1 | server clock；date operators | 只读；客户端提交拒绝 |
| UPDATED_BY | system-computed / member id / 1 | last actor；eq,in | 只读；客户端提交拒绝 |
| UPDATED_AT | system-computed / datetime UTC / 1 | server clock；date operators | 只读；客户端提交拒绝 |

结构化派生字段 AST 最大深度 5、单字段直接依赖最多 10、整模块依赖图无环。允许同记录标量和已声明 relation/subtable 聚合，不允许任意跨模块遍历。依赖更新在保存事务内写重算任务和 outbox；同步可完成的同记录计算必须同事务读回，异步关系聚合保留旧值并暴露 `recalculationState`，失败可重试且不激活错误索引。

测试必须读取机器合同，case 集合精确为 `caseApplicabilityByMode[type.mode]`、`caseApplicabilityByOperatorPolicy[type.operatorPolicy]` 与满足表达式的 `conditionalCaseApplicability` 三者并集，生成 `FT-{ordinal}-{type}-{case}`；只有 applicable case 必须 PASS，不允许运行器自行 skip。WRITABLE/WRITABLE_TRANSITION/SENSITIVE_WRITABLE 的 fixture 必须至少一条 normalization vector；operatorPolicy=EXPLICIT 遍历每个 operator，RESULT_SCHEMA 按 resultTypePolicies 的 result schema、typed column 和 operatorVectorProfile 全展开。REFERENCE/FORMULA/SUMMARY/CALCULATED/LOOKUP 必须分别使用 `derivedFixtureByType` 指定的独立 source/AST、合法结果、重算和异常向量，禁止共用抽象占位 fixture。deferred 只执行 required 激活阻断、API 写拒绝和 UI 控件不存在。证据 manifest 必须覆盖 49 类型的全部 applicable case；未知 mode、缺 fixture/operator/result vector 或 NOT_APPLICABLE 之外的缺行即 Gate 失败。

## 8. 字段变更、索引与回填

| 变更 | 兼容性 | 激活规则 |
|---|---|---|
| label/help/layout/operator 减少 | compatible | 发布检查通过后可激活；saved view 的失效 operator 标记不可用 |
| text 长度增加、nullable 放宽、同类型精度安全扩大 | compatible | 无 backfill，建立新 snapshot |
| nullable 收紧、唯一开启、operator/index 增加 | backfill-required | preview 必须为 0 个非法/重复，index plan READY 后才能 ACTIVE |
| 类型可转换，例如 integer→decimal、date→datetime | migration-required | preview、转换规则、全量 backfill、抽样读回、PASSED 后激活 |
| 类型不兼容、加密策略改变、多值→单值 | migration-required | 必须显式解决每个失败值；未 PASSED 禁止发布/回滚到不兼容版本 |
| 删除字段 | historical-compatible | 新 snapshot 停用；历史 field snapshot/value 永久可解释，不物理级联 |

迁移状态为 `PREVIEWED/BACKFILLING/PASSED/FAILED/CANCELLED`。采用 expand/migrate/contract：先写新投影并双读校验，再按第 5 节的 ACTIVE/candidate 双写、version CAS、水位+delta 追平和写屏障协议回填，再原子切 active pointer；旧 snapshot 在有历史引用时不得删除。失败从最后确认水位恢复；新索引只有全量 anti-join/checksum/unique 扫描为零差异才 ACTIVE，抽样不能代替全量切换检查。多值 index 保留 ordinal，所有排序最终追加 record_id，失败计划不参与查询。

`vs4-field-contract.json` 同时是 49 类型转换注册表：same type 使用 selfConverter；未列 cross-type edge 一律 INCOMPATIBLE。每条 edge 固定 converter/version、lossy、reversible、reverse converter 和 failure policy；lossy 必须逐记录解决。converter/version 写入 migration run，unique/index 按新 generation 重建；只有 edge reversible 且旧 snapshot 保留时才允许 active pointer rollback。

schema generation 固定回归：`SG-01` 在 Vn 创建 Rn 后发布兼容 Vn+1，Rn 仍出现在默认列表/detail/filter/sort/search；`SG-02` Vn+1 新记录与 Rn 的同 logical unique 值冲突；`SG-03` Vn+1 AUTO_NUMBER 接着 Vn sequence 增长；`SG-04` 从 active Vn+1 创建目标为 Vn 的反向 migration 和新 candidate generation，完整 preview/backfill/校验/切换后，Vn/Vn+1 已有记录按目标 capability 可见且编号不回退，并证明旧 SUPERSEDED generation 从未被直接重新激活；`SG-05` backfill FAILED 时 Vn+1 不激活、Vn 查询不受影响；`SG-06` 在候选代扫描过 R100 后并发把 R100 v10 更新到 v11、另写入同 logical unique 值并触发切换，证明双代写、旧版本 CAS 被拒、delta 清零、unique 冲突阻止切换，解除冲突后写屏障等待在途事务且 pointer/status 同事务切换；再注入切换失败，证明本次未提交的 pointer/status 回滚和原 ACTIVE 代查询无缺行。每例保存 logical/snapshot/generation IDs、source record version、delta count、anti-join/checksum/unique scan 和 SQL 读回。

typed index DDL 冻结九列：`string_value VARCHAR(512) COLLATE utf8mb4_bin`、`decimal_value DECIMAL(38,10)`、`integer_value BIGINT`、`date_value DATE`、`datetime_value DATETIME(6)`、`time_value TIME(6)`、`boolean_value TINYINT(1)`、`geo_hash CHAR(12) CHARACTER SET ascii`、`hash_value BINARY(32)`，另有 MONEY companion `currency_code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin`；CHECK 要求九个 typed value 恰有一列非 NULL，并要求 `value_kind=MONEY` 当且仅当 decimal_value 与 currency_code 同时非 NULL，非 MONEY 的 currency_code 必须 NULL。分别建立 `idx_ri_string/decimal/integer/date/datetime/time/boolean/geo/hash`，列顺序为 `system_id,tenant_id,logical_module_id,logical_field_id,index_generation_id,record_status,{typed_value},record_id`；另建 `idx_ri_money`，value 顺序固定 `currency_code,decimal_value,record_id`。MONEY 的 EQ/range/sort/unique 请求和 normalized hash 必须显式绑定 ISO 4217 currency，跨币种拒绝比较且相同 amount 不产生 unique 冲突。JSON declared path 使用 path_snapshot_id 后落到九列之一；RESULT_SCHEMA 在 schema 激活时解析到 resultTypePolicies 的具体列，禁止占位 operator/column 进入 API。SEARCH/RELATION/SUBTABLE 路由各自专表，不伪装 typed column。

record_search 建 `(system,tenant,logical_module,logical_field,index_generation,status,token_hash,record_id)` 和 token_prefix 索引；token_prefix 最长 32 字节，每 field/value 最多 256 tokens，超限阻止激活或写入。token 来源固定 NFKC/lower/CJK bigram。GEO geohash 只生成最多 2,000 个候选，再用 record_value WGS84 坐标执行精确 box/distance 复核，候选超限返回 `QUERY_TOO_BROAD`。SECRET/IDENTITY 只走 HMAC equality hash，不进入普通 token、suggest 或 global search。

## 9. 动作、记录生命周期与草稿

### 9.1 动作矩阵

| action type | VS4 能力 | 服务端行为 |
|---|---|---|
| CREATE | supported | schema-ready、create permission、字段 write 后只创建 DRAFT；提交必须再调用 activate，禁止绕过默认值/草稿语义直接 ACTIVE |
| UPDATE | supported | update permission、field write、CAS、全事务 |
| DELETE | supported-as-trash | 只移入 TRASHED；物理清理不开放 |
| CUSTOM | restricted | 仅下方 endpoint mapping 的结构化 built-in handler；其他自定义 handler 标记 unavailable |
| APPROVAL | deferred-vs6 | 不进入 record-schema/actions；required workflow 阻止激活 |
| IMPORT | deferred-vs5 | 不进入可执行动作集合 |
| EXPORT | deferred-vs5 | 不进入可执行动作集合，B3 批量区不显示 |
| PRINT | deferred-vs5 | 不进入可执行动作集合 |

placement `TOOLBAR/ROW/DETAIL/BATCH` 只影响展示位置，不改变 action permission。服务端根据当前记录状态、版本、scope 和 action capability 逐条过滤可执行动作；客户端提交未返回动作一律拒绝。

| endpoint/command | actionType | handlerId | permissionCode | allowed state |
|---|---|---|---|---|
| `POST records` | CREATE | CREATE_DRAFT | `module.{module}.create` | no record |
| `PUT records/{id}` | UPDATE | UPDATE_RECORD | `module.{module}.update` | DRAFT,ACTIVE |
| `POST {id}:autosave` | UPDATE | AUTOSAVE_DRAFT | `module.{module}.update` | DRAFT |
| `POST {id}:activate` | UPDATE | ACTIVATE_DRAFT | `module.{module}.update` | DRAFT |
| `POST {id}:trash` / batch-trash | DELETE | TRASH_RECORD | `module.{module}.delete` | DRAFT,ACTIVE,ARCHIVED,EXPIRED |
| `POST {id}:discard` | DELETE | DISCARD_DRAFT | `module.{module}.delete` | DRAFT |
| `POST {id}:recover` | CUSTOM | RECOVER_DRAFT | `module.{module}.action.recover_draft` | EXPIRED |
| `POST {id}:duplicate` | CUSTOM | DUPLICATE | `module.{module}.action.duplicate` | ACTIVE,ARCHIVED |
| `POST {id}:transfer` / batch-transfer | CUSTOM | TRANSFER | `module.{module}.action.transfer` | ACTIVE |
| `POST {id}:archive` / batch-archive | CUSTOM | ARCHIVE | `module.{module}.action.archive` | ACTIVE |
| `POST {id}:unarchive` | CUSTOM | UNARCHIVE | `module.{module}.action.unarchive` | ARCHIVED |
| `POST {id}:restore-from-trash` | CUSTOM | RESTORE_TRASH | `module.{module}.action.restore_trash` | TRASHED |
| `POST records:batch-edit` | UPDATE | BATCH_EDIT | `module.{module}.update` | all selected ACTIVE |

record-schema 返回相同 handlerId/actionType/permissionCode/allowedStates/placement/enabled/disabledReason。服务端只接受本次 schema 可返回且当前状态 enabled 的 handler；batch endpoint 对所有 items 执行同一映射。

### 9.2 生命周期

- 状态：`DRAFT/ACTIVE/ARCHIVED/TRASHED/EXPIRED`。
- `DRAFT -> ACTIVE/TRASHED/EXPIRED`；`EXPIRED -> DRAFT/TRASHED`；`ACTIVE -> ARCHIVED/TRASHED`；`ARCHIVED -> ACTIVE/TRASHED`；`TRASHED -> prior_status`。ARCHIVED->ACTIVE 只走 unarchive，TRASHED 恢复只走 restore-from-trash，并重新检查 required、unique、schema 和权限。
- 每个 AUTO_NUMBER 都在首次激活时写入对应 field value；一个 module 最多一个 AUTO_NUMBER 可声明 `record_no_source=true` 并同步 `record.record_no`。并发唯一、单调、允许间隙、永不复用，草稿展示“提交时生成”。
- 动态 UNIQUE 支持机器合同中的 `VALUE` 单值和 PHONE/EMAIL 的 `ELEMENT` 元素唯一。DRAFT 可提示冲突但不占 unique；ACTIVE/ARCHIVED 持有 unique；TRASHED/EXPIRED 释放；激活和从回收站恢复时竞争 `record_unique`，冲突返回字段级 409。
- TRASHED 保留 30 天，VS4 不做物理 purge；90 天无活动 DRAFT 变为 EXPIRED，不静默删除。恢复草稿和恢复记录都保留历史。
- archive 保留查询可见性但默认列表排除；trash 默认全部业务查询排除，回收站入口单独授权。

### 9.3 草稿交互

- 表单停止输入 3 秒后自动保存，字段 blur 也触发；同一时刻只允许一个 in-flight save，后续修改合并到下一次。
- 明确提供“保存草稿”“提交”“放弃草稿”；系统工作台提供“我的草稿”。放弃进入 TRASHED，不能直接物理删。
- 路由离开、关闭详情或切换 context 时，有未提交本地更改必须提示；自动保存成功后可无提示离开。
- 409 冲突返回 currentVersion、current snapshot 和 conflictFields。UI 只提供“重新加载”和“复制为新草稿”，禁止静默覆盖。
- 自动保存失败保留本地输入、标明最后成功时间并可重试；刷新后优先询问恢复服务端草稿，不用 localStorage 作为业务事实。

### 9.4 运行时字段规则与默认值

每次创建/更新/激活都使用 immutable `rule_ast_json`，固定顺序为：`module/field permission 裁剪 -> 条件 visible/readOnly/required 规则 -> 首次草稿默认值 -> type/required/format/unique 校验 -> derived/system 计算 -> 持久化`。

- 默认值 provider 固定 `FIXED`, `CURRENT_MEMBER`, `PRIMARY_DEPARTMENT`, `CURRENT_DATE`, `CURRENT_DATETIME`, `STRUCTURED_FORMULA`, `UPSTREAM_FIELD`。服务端只在首次创建 DRAFT 时为“客户端未提供且规则可见”的字段计算一次；恢复草稿、编辑、自动保存和重试不得重新覆盖。
- 条件规则在依赖字段变化后重新计算。客户端提交 hidden/readOnly 字段稳定返回 `RECORD_FIELD_FORBIDDEN`；服务端 default/system/derived 写入不视为客户端越权。
- 字段变为 hidden 时按 snapshot 的 `clearOnHide` 执行：true 时服务端清除并写历史，false 时保留旧值但不返回/不允许修改。required 只在最终 visible 时生效；readOnly+required 必须由 default/system/derived 满足，否则 schema 激活被阻止。
- 规则 AST 最大深度 5、叶子 20，依赖图无环；权限隐藏优先于所有条件规则，规则不得重新显示无 read 权限字段或放开无 write 权限字段。

## 10. 授权合同

### 10.1 接口与合并算法

core 新增 `RuntimeAuthorizationFacade.resolve(RuntimeAuthorizationRequest) -> RuntimeGrantSnapshot`。request 固定包含 context、permission code、module snapshot 和 actor；snapshot 是纯 core DTO，固定包含 `denied`, `authzEpoch`, role-bound 七类 scope、selected member/department ids、FIELD_RULE AST、readable/writable/sensitiveReadable/sensitiveQueryable field ids 和 executable action ids。plat adapter 只组装 DTO，module 不知道 plat 表结构。

数据范围精确支持七类：`ALL`, `SELF`, `PRIMARY_DEPARTMENT`, `DEPARTMENT_TREE`, `SELECTED_DEPARTMENTS`, `SELECTED_MEMBERS`, `FIELD_RULE`。

1. 先验证 session/context/system/tenant 和 module permission。
2. 同一 permission 在任一生效角色上出现显式 DENY，最终结果为 DENY。
3. 无 DENY 时，对所有 ALLOW 角色各自绑定的数据范围取并集；scope 不脱离 role grant 单独授权。
4. `SELF` 使用 owner_member_id；部门范围使用 owner_department_id 和已冻结 closure；selected members/department 使用同系统 target；FIELD_RULE 使用结构化 AST。module 的唯一 `RuntimeScopePredicateCompiler` 把 snapshot 编译为 record/index SQL predicate，list 和 direct-id 必须走同一 compiler。
5. team membership 只增加该条记录的数据可见候选，永远不授予 module/action/field permission；FOLLOWER 在 VS6 前不产生通知。
6. authz epoch 改变后缓存立即失效；同请求使用一个 snapshot，不能前后半程授权版本不同。
7. 列表、total、suggest、global search、detail、relation target、subtable、team、comment、history 和 batch 复用同一 scope predicate。ArchUnit、Mapper XML scan 和 SQL recorder 必须证明 module 对 `un_plat_*` 引用为 0。

### 10.2 字段与敏感信息

- 每个字段使用独立权限 `module.{module}.field.{field}.read`, `.write`, `.sensitive.read`, `.sensitive.query`。
- 无 read 的字段从 schema、detail、list、search、history diff 和错误参数中全部省略；无 write 的已提交字段使整条命令失败，不静默丢弃。
- IDENTITY/SECRET 使用 AES-GCM encryption key version 和独立 HMAC key version/equality hash，两类 key 分开轮换；轮换期双 key 读取、只用新 key 写，完成 backfill 后停旧 key。无 sensitive.read 时只返回固定掩码/是否已设置；无 `sensitive.query` 时 record-schema 不返回敏感 filter/operator，不能用 equality 探测。列表、search、history、audit、outbox、日志、异常不得出现明文，index 只保存 HMAC hash。
- single direct-id 因不存在或 scope 不可见时统一 HTTP 404/`RECORD_NOT_FOUND`。batch 外层固定 409，item 对这两种情况只返回同一 `RECORD_NOT_FOUND` code。内部分类和 correlationId 只进入受限 security audit；operation audit 仍是外部归一化 code。禁止用 403、不同逐项原因、计数、建议或额外 existence query 泄漏。relation target 无 scope 时整项省略；只有 target record 可见但标题 field 无 read 时才返回无标题占位。
- 前端隐藏只是体验，所有 mutation 必须服务端重算 permission、scope、field 和 action capability。

## 11. 固定查询与 API 合同

### 11.1 查询语法

- 分页固定 `page/size/total`，page 从 1 开始，默认 50，最大 200；浏览器 route、API DTO、saved view 全部同名，本节点不实现 cursor。
- filter 是结构化 AST：`AND/OR` 各有 1..20 个 children，`NOT` 必须且只能有 1 个 child；最大深度 5、最多 20 个叶子。字段和 operator 必须来自 record-schema 且 index plan ACTIVE；MONEY 的 EQ/range operator value 必须同时携带 currency，sort 也必须指定 currency partition。
- sort 最多 3 个声明可排序字段，服务端始终追加 `recordId ASC` 稳定尾序；非法/失效字段返回 `QUERY_FIELD_UNAVAILABLE`。
- q 最大 100 字符；只搜索声明 searchable、本次授权 readable 且 mode 非 sensitive 的 field token，record_no/title 不享有权限旁路。sensitive.query 只开放结构化 filter `EQ/EMPTY` 并走 HMAC index，永久排除 q、record_search、suggest 和 global search。默认最小 2 字符，不允许前置通配全表扫。
- saved view 保存版本化 filter/sort/columns；字段失效时保留视图并返回 invalidNodes，用户修复前不能把失效条件静默忽略。
- `RecordQuery` 固定为 `{schemaVersionId,page,size,recordScope,q,filter,sort,columns,viewId}`；`recordScope=active|archived|trash`，默认 active。archived 需要 `module.{module}.archive.view`，trash 需要 `module.{module}.trash.view`；unarchive/restore-from-trash 使用 9.1 的 action permission。
- filter/sort 使用 canonical UTF-8 JSON：对象 key 字典序、无多余空白、enum 大写、数字十进制规范化、数组保序，合计最大 32 KiB。服务端响应附 canonical query hash；不接受重复 key、未知字段或非 canonical enum。

### 11.2 公共 DTO、版本与幂等

- base path 固定 `B=/api/systems/{systemId}/runtime/modules/{moduleCode}`。
- 全部命名 DTO 和精确 42 项 `x-endpointContracts` 注册表固定在 `rebuild/vs4-api-contract.schema.json`；注册表逐项冻结 method、canonical path、path/query/body/response/error/authorization `$ref`、success status、CSRF、idempotency 和 action/handler/permission/states。前端 types、后端 request/response、OpenAPI、route registry 和 contract tests 必须从该 schema 校验/生成，禁止手写同名异构 DTO；未声明 mutation 必须在路由扫描 Gate 失败。
- `RecordRef={recordId:string,expectedVersion:long}`；create 不传 expectedVersion，以 body 的 immutable `schemaVersionId` 防 stale schema；其余 record mutation 的 expectedVersion 只在 JSON body，不使用 If-Match 双来源。
- mutation 的幂等 scope 固定 `(systemId,tenantId,actorId,HTTP method,canonical path,Idempotency-Key)`，key 为 ASCII binary/VARBINARY、最长 128 bytes、大小写敏感、TTL 24h；body SHA-256 用于同 key 异体冲突。
- create/update/duplicate 使用 `RecordWriteResponse={record:RecordDetailResponse,historyId,correlationId}`；其余 single-record mutation 使用 `RecordMutationResponse={recordId,version,schemaVersionId,status,historyId,correlationId}`，autosave 只返回无 historyId 的 `AutosaveRecordResponse`。验证错误 422，版本/唯一 409，single record 不存在或无 scope 404，未认证 401，模块 permission DENY 403。batch 外层 precondition 一律 409，item 的不存在/无 scope 都是同一个 `RECORD_NOT_FOUND`，不得改变外层状态。
- payload 最大 2 MiB，filter/sort 32 KiB；所有 ID 传 string。错误至少冻结：`RECORD_NOT_FOUND`, `RECORD_VERSION_CONFLICT`, `RECORD_VALIDATION_FAILED`, `RECORD_FIELD_FORBIDDEN`, `RECORD_SCHEMA_STALE`, `RECORD_RELATION_INVALID`, `RECORD_STATE_INVALID`, `RECORD_UNIQUE_CONFLICT`, `FIELD_RUNTIME_UNAVAILABLE`, `QUERY_FIELD_UNAVAILABLE`, `QUERY_SNAPSHOT_EXPIRED`, `SAVED_VIEW_INVALID`, `BATCH_PRECONDITION_FAILED`。

### 11.3 核心 API

| method/path | request | success/status | version/idempotency |
|---|---|---|---|
| `GET B/record-schema` | path only | `RecordSchemaResponse`, 200 | 当前 authz epoch，无幂等 |
| `POST B/records:query` | `RecordQuery` | rows、page/size/total、queryHash、15 分钟 HMAC `querySnapshotToken`, 200 | scope 过滤后 total，无幂等 |
| `GET B/records/{recordId}` | path | `RecordDetailResponse` 含 actions/collaboration counts, 200 | 无 scope 统一 404 |
| `POST B/records` | `CreateRecordRequest` | `RecordWriteResponse`, 201 DRAFT | create 不传 expectedVersion；首次默认/规则只在此执行；CSRF+idempotency |
| `PUT B/records/{recordId}` | `UpdateRecordRequest` | `RecordWriteResponse`, 200 | CSRF+idempotency+CAS |
| `POST B/records/{recordId}:autosave` | `AutosaveRecordRequest` | `AutosaveRecordResponse`, 200 | DRAFT only，CSRF+idempotency+CAS |
| `POST B/records/{recordId}:activate` | `VersionCommandRequest` | `RecordMutationResponse`, 200 | ACTIVATE_DRAFT；CSRF+idempotency+CAS |
| `POST B/records/{recordId}:archive` | `VersionCommandRequest` | `RecordMutationResponse`, 200 | ARCHIVE；CSRF+idempotency+CAS |
| `POST B/records/{recordId}:unarchive` | `VersionCommandRequest` | `RecordMutationResponse`, 200 | UNARCHIVE；CSRF+idempotency+CAS |
| `POST B/records/{recordId}:trash` | `VersionCommandRequest` | `RecordMutationResponse`, 200 | TRASH_RECORD；CSRF+idempotency+CAS |
| `POST B/records/{recordId}:restore-from-trash` | `VersionCommandRequest` | `RecordMutationResponse`, 200 | RESTORE_TRASH；CSRF+idempotency+CAS |
| `POST B/records/{recordId}:discard` | `VersionCommandRequest` | `RecordMutationResponse`, 200 | DISCARD_DRAFT；CSRF+idempotency+CAS |
| `POST B/records/{recordId}:recover` | `VersionCommandRequest` | `RecordMutationResponse`, 200 | RECOVER_DRAFT；CSRF+idempotency+CAS |
| `POST B/records/{recordId}:duplicate` | `VersionCommandRequest` | `RecordWriteResponse`, 201 new DRAFT | DUPLICATE；CSRF+idempotency+CAS source |
| `POST B/records/{recordId}:transfer` | `TransferRecordRequest` | `RecordMutationResponse`, 200 | TRANSFER；target 同 context，CSRF+idempotency+CAS |
| `POST B/records:my-drafts-query` | `DraftQueryRequest` | `RecordListResponse`, 200 | actor 强制当前成员，无幂等 |
| `POST B/records/{recordId}:neighbors` | `NeighborRequest` | `NeighborResponse`, 200 | 每次重算 scope；首尾 neighbor=null/boundary=true；snapshot 失效 409 |

query 成功时生成随机 128-bit snapshot id，Redis key `vs4:query:{snapshotId}` 保存 canonical RecordQuery、queryHash、actor/context/logical module/schema/authzEpoch、稳定 sort 和 TTL=15 分钟；querySnapshotToken 只含 snapshotId/expiry/HMAC。NeighborRequest 另含当前 record 的 sortAnchor，服务端读取完整 query、校验 token/hash/actor/context/epoch，并以同一 scope predicate、稳定 sort anchor 和 `LIMIT 1` 查询；不存在候选才返回 boundary=true，不允许扫描上限冒充首尾。Redis 丢失、过期或 epoch 改变统一 `409 QUERY_SNAPSHOT_EXPIRED`，UI 回保留 query 的列表刷新。

### 11.4 批量 DTO 与能力

- 只支持显式 record id 选择，跨页保留，最多 200 条；不实现“选择当前筛选全部”。选择状态显示 selected N、清空、失败原因。
- VS4 支持 batch edit、transfer、archive、trash；export 隐藏至 VS5。
- `POST B/records:batch-capabilities` 接收 `BatchCapabilityRequest`，固定 200 返回 `BatchCapabilityResponse`。主动作对 mixed selection 不隐藏；只要 eligibleCount != selectedCount 就禁用。不存在和无 scope 都计入同一个 `NOT_FOUND_OR_NO_SCOPE`，不得区分；其他稳定原因 `ACTION_FORBIDDEN`, `STATE_INVALID`, `VERSION_STALE`, `FIELD_NOT_BATCH_EDITABLE`。
- `POST B/records:batch-edit` 接收 `BatchEditRequest`。SET 替换 scalar/multi，CLEAR 写 null/空集合，ADD/REMOVE 只用于机器合同 maxCardinality>1 的普通 value 字段；RELATION/SUBTABLE/SECRET/derived/system 不支持 batch-edit。
- `POST B/records:batch-transfer` 接收 `BatchTransferRequest`；`POST B/records:batch-archive` 与 `POST B/records:batch-trash` 接收 `BatchCommandRequest`。
- 服务端先对全部 id 检查存在性/scope/action/field permission/state/version/cardinality，再在一个事务中执行。任一失败返回 409 `{allApplied:false,items:[{recordId,resultCode}]}` 且数据库零变化；不存在和无 scope 的 resultCode 都是 `RECORD_NOT_FOUND`。
- 成功统一返回 200 `{allApplied:true,items:[{recordId,resultCode:"APPLIED",newVersion,historyId}]}`；失败返回 `{allApplied:false,items:[{recordId,resultCode}]}` 且 item 禁止出现 newVersion/historyId。两种 item 由 schema `oneOf` 严格区分并写逐记录 history/audit/outbox。四个 mutation 都要求 CSRF+idempotency；同 key 同 payload返回当前授权下重新投影的结果，不同 payload 冲突。

### 11.5 协作和效率 API

系统级 base path 固定 `S=/api/systems/{systemId}/runtime`。

| method/path | request/result |
|---|---|
| `GET B/records/{id}/relations/{fieldCode}?page={page}&size={size}` | 200 关系分页；size max 100 |
| `POST B/records/{id}/relations/{fieldCode}:mutate` | `{expectedVersion,add[],remove[],order[]}`；200 record version；CSRF+idempotency+CAS |
| `GET B/records/{id}/subtables/{fieldCode}?page={page}&size={size}` | 200 子表分页；size max 100 |
| `POST B/records/{id}/subtables/{fieldCode}:mutate` | `{expectedVersion,add[],update[],remove[],order[]}`；200 parent version；CSRF+idempotency+CAS |
| `GET B/records/{id}/team` | 200 team list |
| `POST B/records/{id}/team:mutate` | `{expectedVersion,add[],changeRole[],remove[]}`；200 version；CSRF+idempotency+CAS |
| `GET B/records/{id}/comments?page={page}&size={size}` | 200 评论分页，size max 100 |
| `POST B/records/{id}/comments` | `{recordVersion,parentCommentId?,body}`；201 comment/version；CSRF+idempotency |
| `PUT B/records/{id}/comments/{commentId}` | `{commentVersion,body}`；200 comment/version；CSRF+idempotency+CAS |
| `DELETE B/records/{id}/comments/{commentId}` | `{commentVersion}`；200 tombstone/version；CSRF+idempotency+CAS |
| `GET B/records/{id}/history?page={page}&size={size}` | 200 append-only history，size max 100 |
| `GET S/saved-views?moduleCode={moduleCode}` | 200 current-member views |
| `POST S/saved-views` | `{moduleCode,name,query,columns}`；201 id/version；CSRF+idempotency |
| `PUT S/saved-views/{viewId}` | `{expectedVersion,name,query,columns}`；200 id/version；CSRF+idempotency+CAS |
| `DELETE S/saved-views/{viewId}` | `{expectedVersion}`；200 deleted/version；CSRF+idempotency+CAS |
| `GET S/favorites?page={page}&size={size}` | 200 MODULE/RECORD favorites，size max 100 |
| `POST S/favorites` | `{type:MODULE|RECORD,moduleCode,recordId?}`；201/upsert 200；CSRF+idempotency |
| `DELETE S/favorites/{favoriteId}` | `{expectedVersion}`；200 deleted/version；CSRF+idempotency+CAS |
| `GET S/recent?page={page}&size={size}` | 200 current-member recent，size max 50 |
| `POST S/search` | `{q,providers,page,size}`；200 provider/module groups；逐 scope/field 授权 |

所有协作 mutation 都要求 CSRF、Idempotency-Key 和 body version；create comment 用当前 recordVersion 防向已失权/已变化记录写入。RelationInput/RelationMutationRequest、SubRowInput/SubtableInput/SubtableMutationRequest、TeamInput/TeamMutationRequest、Comment*、History*、SavedView*、Favorite*、Recent*、Search* 均为 schema 中 `additionalProperties:false` 的命名 DTO，ID/version/ordinal/value/role/cardinality 不得以任意 object 代替。任何 endpoint 未在本表、核心 API 表和机器 `x-endpointContracts` 同时出现即不属于冻结合同，新增必须重新 contract review。

## 12. 协作合同

### 12.1 关联

- 用户可搜索有权目标、添加、移除、重排和打开目标；最多 500/field，重复目标拒绝。
- 目标详情因 scope 不可见时返回 404，关系列表中整项省略；只有目标 record 可见但标题 field 无 read 时才显示无标题占位，不泄漏标题或目标字段。
- 打开目标后返回保持原模块 query/scroll/record 上下文。

### 12.2 子表

- 每行有稳定 row id、version 和 ordinal；支持添加、编辑、删除、重排、字段校验和 schema min/max rows，最多 200/field。
- 父记录保存与子表 mutation 在一个事务中；任一 row/field 无权限或冲突则整次保存失败。并发重复 row id 只允许一个成功。

### 12.3 团队

- 角色固定 `OWNER/COLLABORATOR/VIEWER/FOLLOWER`，支持添加、改角色、移除；最多 200 人，活动成员唯一。
- OWNER 与 record owner 同步；转交同时更新 owner/team/history。团队只影响 record visibility candidate，实际操作仍要求 action/field permission。
- FOLLOWER 不在 VS4 创建通知、待办或 @ 提醒。

### 12.4 评论

- 支持创建、一级回复、编辑/删除自己的评论；`comment.manage` 可 pin、resolve 和管理他人评论。正文最多 4000 字符，服务端净化。
- 回复深度超过 1、跨记录 parent、重复幂等请求拒绝。@、文件评论和通知归后续切片。

### 12.5 历史

- append-only，默认每页 50、最大 100，不在 VS4 清理；显示 actor、时间、动作、版本和字段 diff。
- 无字段 read 不显示该字段；敏感字段永远只显示“已修改”，不保存前后明文。历史无更新/删除 API。

## 13. 效率入口合同

系统 shell 提供命令中心，`Ctrl/Cmd+K` 打开。provider 冻结如下：

| provider | VS4 行为 | 边界 |
|---|---|---|
| CURRENT_SYSTEM_MODULE_MENU | 搜索当前系统有 view permission 的模块/菜单并跳转 | 不搜索其他 system，不进入平台管理 |
| RECORD | 按 readable field token 搜索动态记录、按模块分组并跳转详情 | 无 scope/field read 不命中、不计数 |
| FAVORITE | 搜索当前成员模块/记录收藏 | 失权目标过滤 |
| RECENT | 搜索最近成功打开的记录 | 失权/过期目标过滤 |
| QUICK_CREATE | 列出 create permission 且 runtime-ready 的模块并跳转 `?mode=create` | 无能力模块不显示 |
| TODO_TASK | deferred-vs6 | 不展示入口 |
| PLATFORM_SYSTEM | excluded | 系统切换器已有独立入口，不混入当前系统命令中心 |
| ADMIN_CONFIG | excluded | 管理导航已有独立入口，普通成员命令中心不得提升可发现性 |

- RECORD 结果跳转 `/systems/:systemId/modules/:moduleCode?record={id}`；菜单和 quick-create 使用同一模块 route。
- favorite 类型固定 `MODULE/RECORD`，每成员最多 500；目标失效或无权时查询过滤并允许清理。
- recent 在详情成功读回后 upsert，最多 50、保留 90 天；失败/无权访问不写最近。
- quick create 只列出具有 create permission 且 runtime schema ready 的模块；直接打开该模块新建表单。
- 模块工作台固定提供 `recordScope=active|archived|trash` 三个入口和“我的草稿”：active 默认；archived/trash 仅在对应 view permission 时出现；空态说明当前范围；恢复成功跳转 prior_status 对应范围并刷新 total。
- 待办、任务、日志、扫码、拍照在本节点均不展示伪入口。

## 14. 前端与可访问性合同

### 14.1 路由和上下文

- 主路由：`/systems/:systemId/modules/:moduleCode`。
- query state 固定 `viewId,q,page,size,recordScope,sort,filter,columns,record,mode,draft`；scalar 使用 percent encoding，filter/sort/columns 使用无 padding base64url 包装 11.1 canonical JSON，单 URL 最大 4096 字符，超限必须先保存 view。
- 优先级为显式 q/filter/sort/columns > viewId 保存值 > schema default；page/size/recordScope 始终显式覆盖。q/filter/sort/viewId/recordScope 改变时 page 重置 1；size 改变时 page 调整到包含原首行的页。
- URL normalizer 对未知 key、非法 base64/JSON、越界 page/size、失效 field/operator 不发请求，删除非法项、保留其余合法条件并显示 `URL_QUERY_INVALID`；服务端 422 也执行同一恢复动作，不静默改写条件。
- mode 枚举只允许 `create|view|edit`。合法组合：纯列表无 mode/record/draft；新建 `?mode=create` 且无 record/draft；详情 `?record={id}&mode=view`；编辑 ACTIVE `?record={id}&mode=edit`；编辑/恢复草稿 `?draft={id}&mode=edit`。record 与 draft 互斥，其他组合一律 `URL_QUERY_INVALID`。
- 完整示例：详情 `/systems/S1/modules/order?page=1&size=50&record=100&mode=view`；编辑 `/systems/S1/modules/order?record=100&mode=edit`；quick-create `/systems/S1/modules/order?mode=create`；创建 DRAFT 读回后 replace 为 `/systems/S1/modules/order?draft=101&mode=edit`；我的草稿恢复使用同一 draft URL。
- selection ids/versions、scrollTop、focusedRecordId 只存 `history.state`，以 `systemId/moduleCode/queryHash` 隔离，最多 200；不得进入可分享 URL。打开/关闭详情、创建和编辑时保持列表 query/selection/scroll，关闭后恢复原行和焦点。
- 浏览器前进/后退必须恢复相同 list/detail/form 状态；context 改变时清理旧 system 的 query snapshot、selection 和草稿缓存，并重新获取 authz/schema。

### 14.2 视图规格

- desktop `>=1200px`：密集表格行高 36-44px，稳定列宽/固定列/列显示与顺序，右侧详情宽 720px 且列表仍可见。
- compact `768-1199px`：表格保持核心列，详情以最大 600px overlay；不得产生页面级横向滚动，局部表格横向滚动必须可见。
- mobile `<=767px`：列表卡片、筛选抽屉、全屏详情和全屏表单；不缩放桌面宽表。
- mobile 通过“选择”进入多选模式，卡片显示 checkbox，底部固定动作栏显示 selected N 和更多动作抽屉；跨页选择仍保留，退出模式必须确认清空。
- 动态表单使用分组单列/双列容器，不为每个字段套卡片；required/read-only/hidden/error 与服务端一致。详情 tabs 固定基本信息、关联、子表、团队、评论、历史。
- loading、empty、no-permission、validation、backend error、offline、conflict、draft、saving、saved、success readback 均有可区分状态；错误必须定位字段并保留输入。

### 14.3 交互质量

- 所有图标按钮使用现有 lucide 图标并有 tooltip/accessible name；对话框有初始焦点、focus trap、Esc/关闭和焦点返回。不只依赖颜色表达状态。

| keyboard case | 固定行为 |
|---|---|
| Tab / Shift+Tab | 只遍历当前可见交互元素；drawer/dialog 内循环，关闭后回触发元素 |
| Enter / Space | 按钮、checkbox、row 主操作按原生语义触发；表格 Enter 打开详情，Space 只切选择不滚页 |
| Arrow keys | tabs 左右移动并激活；菜单/combobox 移动 active option；不劫持普通文本光标 |
| Table Up/Down/Home/End | desktop/compact 表格使用 roving tabindex；Up/Down 移相邻可见行，Home/End 移本页首尾，翻页后焦点落新页首行；Enter 打开、Space 选择 |
| Escape | 依次关闭最内层 menu/dialog/drawer；有未保存输入先提示，不直接丢弃 |
| Ctrl/Cmd+K | 任意 system runtime 页面打开命令中心，初始焦点在搜索框；关闭回原焦点 |
| conflict recovery | 409 dialog 初始焦点在“重新加载”，复制草稿后焦点到首个 conflict field |

| error | 展示位置与输入 | 允许的恢复动作 |
|---|---|---|
| 401 | 全屏 session expired，内存中未提交输入保留到重新登录结果 | 登录后回原 route 并询问恢复 |
| 403 module permission | 页面 no-permission，不显示业务计数 | 返回系统工作台 |
| 404 record | 详情内 not-found，不改变列表 query/selection | 关闭详情回原列表 |
| 409 version/unique/batch | dialog + field/row 定位，保留全部输入并显示 correlationId | reload 或复制草稿；batch 只关闭并刷新 versions |
| 422 validation/query | form/筛选内联错误 + summary，焦点首错，保留合法输入 | 修正后重试 |
| offline/network | 顶部 persistent banner，保留输入，禁止显示保存成功 | 联网后显式重试 |
| 429 | 当前控件/页面显示 Retry-After 倒计时，保留输入，倒计时前禁用重试 | 倒计时结束后同 Idempotency-Key 重试 mutation |
| 5xx | 当前工作区 error state，显示本次 correlationId，不清空输入/query | mutation 复用 Idempotency-Key，但每次请求生成全新 requestId/correlationId |

- 三视口均执行 axe，无 critical/serious；浏览器 console error、pageerror、unexpected failed request 和页面级 overflow 为 0。
- 必须保留截图：desktop 1440x900、compact 1199x720、边界 1200x720、mobile 390x844，以及 767x844/768x844 断点；每个视口执行 overflow/axe。最长中文/英文错误和 200 条分页不得撑破容器。

## 15. 容量、性能与查询计划 Gate

### 15.1 固定数据集和阈值

- seed 固定字符串 `VS4-PERF-SEED-V1:20260715`，SHA-256 `b89b13fac77ca7f67d3ab8cde1d0d2d47f7681c4a5a16fd1218ff4cc668494f0`。数据生成器不得使用系统时间；输出 manifest 记录脚本 SHA、逐表 row count/min-max id/checksum，两次生成 manifest 必须一致。
- 数据集：1 system、1 tenant、1 module、1,000,000 records、每记录 20 个普通字段、10% archived、10,000 members、50 departments；5 个单值索引、2 个多值索引；30% records 有 2 relations、20% 有 5 sub rows、10% 有 3 team members、10% 有 5 comments。
- 基准资源固定为 MySQL container 4 vCPU/8 GiB、app 4 vCPU/4 GiB、同机 NVMe、MySQL buffer pool 4 GiB；Hikari maxPool=100/minIdle=10。若实际硬件低于基准只能记录诊断，不能据此把阈值改低；硬件/OS/Docker/MySQL/JVM 参数完整入 manifest。
- Read run：closed-loop 100 VU，70% list、20% detail、10% search，预热 1 分钟、测量至少 5 分钟并持续到每个 query case 至少 1,000 samples、list 总样本至少 10,000、detail 至少 3,000；两项条件都满足才停止。
- Write run：独立 closed-loop 20 VU，60% create-draft+activate、30% update、10% comment，预热 1 分钟、测量至少 5 分钟并持续到三类各至少 1,000 samples；两项条件都满足才停止，不与 Read run 混合计算 percentile。
- Browser run：1440x900 普通成员、warm API cache，清理 browser cache 后独立执行 30 次 shell 首屏，Playwright trace 以 navigation start 到关键列表可交互 mark 计算 P75。
- percentile 使用 HDR histogram 1ms precision 和 nearest-rank；HTTP 非预期状态、超时、响应 schema 错误率必须 0。阈值继承设计包：indexed common list P95 `<=1.5s`；detail P95 `<=800ms`；ordinary write P95 `<=1.5s`；system shell first screen P75 `<=2.5s`，不能通过减少合同字段规避。

| case | exact query / binding | expected selectivity |
|---|---|---|
| Q01 | record_no EQ `R-0500000`, page=1,size=50,recordScope=active | 1 |
| Q02 | TEXT prefix `华东-20`, sort text ASC | 1,000-20,000 |
| Q03 | NUMBER between `1000,2000`, sort number DESC | 5%-15% |
| Q04 | DATE between `2026-01-01,2026-03-31`, sort date ASC | 20%-30% |
| Q05 | MEMBER hasAny `[M00042]` | 0.5%-5% |
| Q06 | DEPARTMENT inTree `D010`, sort updatedAt DESC | 5%-20% |
| Q07 | Q02 AND Q03 | 0.1%-5% |
| Q08 | Q02 AND Q03 AND Q04, 3 sorts + recordId | 0.01%-2% |
| Q09 | q=`合同2026` using readable field token | 100-10,000 |
| Q10 | recordScope=archived, page=1,size=200 | 10% base |
| Q11 | filter AST depth=5/leaves=20 at legal maximum | deterministic fixture IDs |
| Q12 | each SC-01..SC-07 scope with same Q07 | exact IDs from 16.3 |

### 15.2 EXPLAIN 规则

- record/value/index 主查询不得对百万事实表出现 access type `ALL`；chosen key 非 null，tenant/module/scope/filter 进入 key range。
- 每个 Q01..Q12 保存 canonical AST、绑定参数、generated SQL、预期 ID/cardinality、`EXPLAIN FORMAT=JSON` 和 `EXPLAIN ANALYZE` 原始输出。slow log `Rows_examined` 与 performance_schema statement digest 计算实际 rows examined P95 `<=50,000`；静态 EXPLAIN estimate 不冒充实际值。
- 不得出现磁盘临时表或无界 filesort。稳定 record_id 尾序必须进入可解释计划。
- 不支持高效执行的 operator 在发布或 query validation 阶段明确拒绝，不能运行后超时。
- 性能脚本、seed manifest、负载参数、HDR 原始结果、Playwright traces、slow log/performance_schema 摘要和 EXPLAIN 必须进入 evidence，可在干净环境复跑。

## 16. 故障、并发与安全矩阵

### 16.1 事务故障点

测试 profile 提供稳定 failpoint：`FP01_RECORD`, `FP02_VALUE`, `FP03_UNIQUE`, `FP04_INDEX`, `FP05_SEARCH`, `FP06_RELATION`, `FP07_SUBTABLE`, `FP08_TEAM`, `FP09_COMMENT`, `FP10_HISTORY`, `FP11_SAVED_VIEW`, `FP12_FAVORITE`, `FP13_RECENT`, `FP14_SUCCESS_AUDIT`, `FP15_OUTBOX`，都表示对应 write 之后立即抛错。生产 profile 若配置任一 failpoint 必须拒绝启动。

| operation | required failpoints |
|---|---|
| CREATE / UPDATE | FP01,02,03,04,05,06,07,08,10,14,15 |
| AUTOSAVE | FP01,02,04,05,10,14,15 |
| ACTIVATE | FP01,02,03,04,05,10,14,15 |
| ARCHIVE / TRASH | FP01,03,04,05,10,14,15 |
| UNARCHIVE | FP01,03,04,05,10,14,15 |
| RESTORE_FROM_TRASH | FP01,02,03,04,05,10,14,15 |
| DISCARD | FP01,04,05,10,14,15 |
| RECOVER | FP01,02,04,05,10,14,15 |
| DUPLICATE | FP01,02,04,05,06,07,08,10,14,15 |
| RELATION_MUTATE | FP06,10,14,15 |
| SUBTABLE_MUTATE | FP02,04,07,10,14,15 |
| TEAM_MUTATE / TRANSFER | FP01,08,10,14,15 |
| COMMENT_CREATE / UPDATE / DELETE | FP09,10,14,15 |
| SAVED_VIEW_CREATE / UPDATE / DELETE | FP11,14,15 |
| FAVORITE_ADD / DELETE | FP12,14,15 |
| RECENT_UPSERT | FP13,14 |
| BATCH_EDIT | FP01,02,03,04,05,10,14,15 |
| BATCH_TRANSFER | FP01,08,10,14,15 |
| BATCH_ARCHIVE / BATCH_TRASH | FP01,03,04,05,10,14,15 |

每个 operation × failpoint 组合固定执行 3 轮。每轮证明：命令失败、所有业务表零残留或保持旧版本、当前 HTTP/SQL 读回不变、success audit/outbox 不存在、脱敏 failed audit 恰好一条、idempotency 按确定性/瞬态合同处理、日志无敏感明文。

V4 migration ALTER 现有 `un_sys_idempotency`：新增 `lease_token CHAR(64) CHARACTER SET ascii COLLATE ascii_bin`，把 `idempotency_key` 改为 `VARBINARY(128)` 并保持 scope unique 大小写敏感，复用 `locked_until`，CHECK status 扩为 `PROCESSING/COMPLETED/FAILED/RETRYABLE`；scope_key 写上述幂等 scope tuple 的 SHA-256 hex 64 字符。状态转移只允许：insert->PROCESSING；PROCESSING+matching lease->COMPLETED(owner commit)；PROCESSING+matching lease->FAILED(deterministic 4xx)；PROCESSING+matching lease->RETRYABLE(transient)；RETRYABLE 或 expired PROCESSING 以 `WHERE status/locked_until/lease_token` CAS 获得新 lease。COMPLETED/FAILED 在 TTL 内只重放，不再执行命令或重复写审计。

幂等 coordinator 固定三段：短事务预留 PROCESSING lease 30 秒；owner transaction 锁定并验证 lease owner，原子写业务事实、success operation audit、outbox 和 idempotency COMPLETED，但 result 只保存内部 `MutationReceipt={resultType,recordId/version/historyId 或 batch receipts,correlationId}`，禁止缓存带 values/标题/敏感字段的 HTTP response；owner rollback 后 exception handler 以 `REQUIRES_NEW` 且 `WHERE status=PROCESSING AND lease_token=?` CAS 写一次脱敏 failed/denied audit并转 FAILED/RETRYABLE，CAS 失败不得补写第二条。COMPLETED 重放不重新执行 mutation，而是使用当前 `RuntimeGrantSnapshot/authzEpoch`、当前 record scope 和字段/sensitive 权限从 receipt 重新构造投影；当前无 scope 统一 404，撤销字段或 sensitive 权限后不得返回旧值。operation audit 只存外部归一化 code；受限 `un_audit_security` 存内部拒绝分类、target HMAC hash 和 correlationId，禁止字段值/目标明文。重复响应不重复写 failed/denied audit。

### 16.2 并发和幂等

- 所有竞争使用独立 HTTP session 和独立 DB connection，在服务端 barrier 确认全部 ready 后同时释放；owner transaction isolation 固定 `READ_COMMITTED`，DB lock timeout 5s、HTTP timeout 10s，记录线程/事务时间线和重试次数。
- 同 record/version 双写重复 20 轮：每轮恰好 1 success、1 `409 RECORD_VERSION_CONFLICT`，最终 version 连续且 history/success audit/outbox 各一条。
- auto number 每轮 100 并发激活、固定 5 轮：每轮 100 个唯一编号、单调、无复用；失败允许间隙。
- idempotency 同 key/同 payload、同 key/不同 payload、expired key 三类分别执行 20 轮，并覆盖 success、deterministic failure 和 transient retry。
- success 后依次撤销普通字段 read、sensitive.read 和 record scope，再以同 key 重放；三例都证明 mutation/history/audit/outbox 不重复，返回按新 epoch 省略字段/掩码或统一 404，缓存 receipt 和日志无旧明文。
- relation 重复目标、subtable 重复 row id、favorite upsert、recent upsert 四类分别执行 20 轮；每轮唯一事实一条，失败方稳定冲突，recent 时间为最新成功 detail readback。
- batch 中一条 version 冲突和一条 scope 冲突分别执行 20 轮：每轮全部 0 变化，逐 id 外部结果与双审计合同一致。

每轮结果必须记录最终 row count、record version、history/success audit/outbox 数量和 correlation ids；死锁只能按同 idempotency key 最多重试一次，仍失败即 Gate 失败。

### 16.3 权限与恢复

- 七类 scope 各有 allow/deny、多角色并集、显式 DENY、epoch 变化和 direct-id 404 正反例。
- field read/write/sensitive、action placement、team visibility、relation target、list total/search suggestion 不泄漏。
- MySQL/Redis 短暂不可用后恢复：未确认写不产生半状态；重试符合幂等；缓存恢复后以 DB/authz epoch 为准。

actor 固定 M1、primary department D1；D2 是 D1 子部门。记录别名/API id/version 固定：R100/100/v10(owner M1,D1,EAST)、R101/101/v11(M2,D1,EAST)、R102/102/v12(M3,D2,WEST)、R103/103/v13(M4,D3,EAST)、R104/104/v14(M5,D4,WEST)、R105/105/v15(M6,D5,EAST)、R106/106/v16(M7,D6,WEST)，另有跨 tenant TX1/900。七条记录的普通可搜索字段 `scopeProbe` 均为 `scope-token` 且测试角色可读；SEARCH 固定 `q=scope-token,providers=[RECORD],page=1,size=100`，因此搜索 expected IDs 与各 SC 表完全相同。selected department=D3，selected member=M5，FIELD_RULE=`region EQ EAST`。

| case/scope | allow 时精确 expected IDs |
|---|---|
| SC-01 ALL | R100,R101,R102,R103,R104,R105,R106 |
| SC-02 SELF | R100 |
| SC-03 PRIMARY_DEPARTMENT | R100,R101 |
| SC-04 DEPARTMENT_TREE | R100,R101,R102 |
| SC-05 SELECTED_DEPARTMENTS | R103 |
| SC-06 SELECTED_MEMBERS | R104 |
| SC-07 FIELD_RULE | R100,R101,R103,R105 |

每个 SC 的 MULTI_ROLE_UNION 第二角色和期望固定：SC01+SELF=SC01；SC02+SELECTED_MEMBERS=`R100,R104`；SC03+SELECTED_MEMBERS=`R100,R101,R104`；SC04+SELECTED_DEPARTMENTS=`R100,R101,R102,R103`；SC05+SELF=`R100,R103`；SC06+SELF=`R100,R104`；SC07+SELECTED_MEMBERS=`R100,R101,R103,R104,R105`。

| variant | rolesBefore -> rolesAfter | target / expected / HTTP |
|---|---|---|
| ALLOW | one allow role for SC -> same | LIST/TOTAL/SEARCH/RELATION/BATCH_EDIT expected SC IDs, 200 |
| NO_GRANT | none -> none | empty, 200；DETAIL R100=404 |
| EXPLICIT_DENY | allow role + DENY role -> same | empty, 200；DETAIL first SC ID=404 |
| MULTI_ROLE_UNION | allow role + fixed second allow -> same | exact union above, 200 |
| CROSS_TENANT | allow role -> same | TX1 excluded；DETAIL TX1=404 |
| EPOCH_REFRESH | allow role at epoch N -> no grant at N+1 | before=SC IDs, after=empty；old cache forbidden |
| DETAIL_VISIBLE | allow role -> same | target=first expected SC ID, 200 |
| DETAIL_HIDDEN | allow role -> same | target=R106 when not expected, otherwise TX1, 404 |
| BATCH_ITEM_HIDDEN | allow role -> same | first expected ID + hidden target，outer 409，zero change |

每个 SC-01..07 对 `LIST/TOTAL/SEARCH/RELATION_TARGET` 执行前六个适用 variant；DETAIL 只执行 DETAIL_VISIBLE/DETAIL_HIDDEN；BATCH 只执行 ALLOW/BATCH_ITEM_HIDDEN/EXPLICIT_DENY/EPOCH_REFRESH。BATCH_EDIT ALLOW 的固定单项分别为 SC01=R106/v16、SC02=R100/v10、SC03=R101/v11、SC04=R102/v12、SC05=R103/v13、SC06=R104/v14、SC07=R105/v15，command 均为 `SET scopeProbe=batch-{SC}`，成功后 expectedVersion 精确 +1 且 HTTP/SQL 读回该值；每例前重置 seed。BATCH_ITEM_HIDDEN 在对应 visible item 后追加本 SC 的 hidden target 和固定原版本，断言 outer 409、两项版本和值均不变。另固定 `SC-BATCH-TRANSFER`=SC01/R101/v11 -> M8（v12/owner M8）、`SC-BATCH-ARCHIVE`=SC04/R102/v12（v13/ARCHIVED）、`SC-BATCH-TRASH`=SC07/R105/v15（v16/TRASHED），分别执行 allow 与 hidden-item 零变化 smoke。case id 固定 `SC-{nn}-{operation}-{variant}`。每例保存 rolesBefore/rolesAfter、epoch、grant snapshot、targetRecordId、items/expectedVersions/command、canonical AST、绑定值、generated SQL、expected/actual IDs、expected/actual HTTP、新版本/读回、total 和 EXPLAIN；不再构造无意义的 LIST×DIRECT_ID 笛卡尔积。

同一记录另建两个角色：Role-A 可读 searchable field，Role-B 只有 module view。相同 q 对 A 命中、对 B 不命中且 total/suggestion 均 0；两者 generated SQL 都以 readable field id 集合约束 token。warm cache 各 100 次的查询计划形状一致，B 不执行隐藏 field token probe，响应中位数比不得因额外 existence query 超过 2 倍。

## 17. 浏览器旅程与证据

每条旅程使用真实 MySQL、真实 HTTP、普通成员账号和服务端权限，不使用页面级 mock。

### 17.1 子能力状态账本

状态只允许 `NOT_STARTED -> IMPLEMENTED -> TESTED -> ACCEPTED`；后续切片使用 `DEFERRED`。测试和 evidence 未填前不得推进状态，整条 journey 继续为 PARTIAL。

| capability id | result | current status | targetSlice | testId | evidencePath |
|---|---|---|---|---|---|
| VS4-B3-01 | q/filter/sort/page/recordScope 查询 | NOT_STARTED | - | pending | pending |
| VS4-B3-02 | 列配置和 saved view invalid 修复 | NOT_STARTED | - | pending | pending |
| VS4-B3-03 | 跨页显式选择、capability/禁用原因/清空 | NOT_STARTED | - | pending | pending |
| VS4-B3-04 | batch edit SET/CLEAR/ADD/REMOVE | NOT_STARTED | - | pending | pending |
| VS4-B3-05 | batch transfer | NOT_STARTED | - | pending | pending |
| VS4-B3-06 | batch archive + archived entry | NOT_STARTED | - | pending | pending |
| VS4-B3-07 | batch trash + trash/restore entry | NOT_STARTED | - | pending | pending |
| VS4-B3-08 | 打开/关闭详情保留 query/selection/scroll | NOT_STARTED | - | pending | pending |
| VS4-B3-09 | selected export | DEFERRED | VS5 | BOUNDARY-VS4-B3-09 | planned:.cursor/session/evidence/vs4/boundaries/VS4-B3-09.json |
| VS4-B4-01 | schema-driven create/edit + 49 field capability | NOT_STARTED | - | pending | pending |
| VS4-B4-02 | permission/rule/default/required order | NOT_STARTED | - | pending | pending |
| VS4-B4-03 | 3s/blur autosave + failure retry | NOT_STARTED | - | pending | pending |
| VS4-B4-04 | manual draft/my drafts/recover/discard/expire | NOT_STARTED | - | pending | pending |
| VS4-B4-05 | version conflict reload/copy draft | NOT_STARTED | - | pending | pending |
| VS4-B4-06 | validation/unique/auto-number/state transition | NOT_STARTED | - | pending | pending |
| VS4-B4-07 | activate and success readback | NOT_STARTED | - | pending | pending |
| VS4-B4-08 | attachment/image/file/signature controls | DEFERRED | VS5 | BOUNDARY-VS4-B4-08 | planned:.cursor/session/evidence/vs4/boundaries/VS4-B4-08.json |
| VS4-B4-09 | AI_FILL | DEFERRED | VS10 | BOUNDARY-VS4-B4-09 | planned:.cursor/session/evidence/vs4/boundaries/VS4-B4-09.json |
| VS4-B5-01 | detail summary/tabs/previous-next/list restore | NOT_STARTED | - | pending | pending |
| VS4-B5-02 | relation add/remove/reorder/open target | NOT_STARTED | - | pending | pending |
| VS4-B5-03 | subtable add/edit/delete/reorder | NOT_STARTED | - | pending | pending |
| VS4-B5-04 | team/transfer/roles | NOT_STARTED | - | pending | pending |
| VS4-B5-05 | comment/reply/edit/delete/manage | NOT_STARTED | - | pending | pending |
| VS4-B5-06 | append-only history/diff/sensitive mask | NOT_STARTED | - | pending | pending |
| VS4-B5-07 | archive/trash/restore lifecycle | NOT_STARTED | - | pending | pending |
| VS4-B5-08 | attachments/print | DEFERRED | VS5 | BOUNDARY-VS4-B5-08 | planned:.cursor/session/evidence/vs4/boundaries/VS4-B5-08.json |
| VS4-B5-09 | approval/history/@ notification | DEFERRED | VS6 | BOUNDARY-VS4-B5-09 | planned:.cursor/session/evidence/vs4/boundaries/VS4-B5-09.json |
| VS4-B11-01 | command center module/menu providers | NOT_STARTED | - | pending | pending |
| VS4-B11-02 | field-authorized record search/jump | NOT_STARTED | - | pending | pending |
| VS4-B11-03 | module/record favorites | NOT_STARTED | - | pending | pending |
| VS4-B11-04 | recent only after successful detail | NOT_STARTED | - | pending | pending |
| VS4-B11-05 | quick create runtime-ready module | NOT_STARTED | - | pending | pending |
| VS4-B11-06 | my drafts continuation | NOT_STARTED | - | pending | pending |
| VS4-B11-07 | todo/task/log | DEFERRED | VS6 | BOUNDARY-VS4-B11-07 | planned:.cursor/session/evidence/vs4/boundaries/VS4-B11-07.json |
| VS4-B11-08 | scan/photo | DEFERRED | VS5 | BOUNDARY-VS4-B11-08 | planned:.cursor/session/evidence/vs4/boundaries/VS4-B11-08.json |

`VS4CapabilityLedgerValidator` 读取此表并要求 35 个固定 ID 恰好一次、任何 Markdown 数据行都可解析。状态只允许上述五种；与 `.cursor/session/state.json` 中上一状态比较，禁止回退、跳过 IMPLEMENTED/TESTED 或从 DEFERRED 改为已完成。TESTED 必须有 testId，ACCEPTED 必须同时有 testId 和已存在 evidencePath；DEFERRED 必须有 targetSlice、BOUNDARY testId 和 planned boundary 路径。validator 在每次 Gate 和 CI 执行。

### 17.2 固定浏览器 case

| case | fixed journey |
|---|---|
| E2E-B3-{1440,1199,390} | B3-01..08：查询、view、跨页选择、四类 batch、archive/trash/restore、详情上下文；无权/version stale/禁用原因 |
| E2E-B4-{1440,1199,390} | B4-01..07：默认/规则、field error、auto/manual draft、离开恢复、冲突复制、unique、auto-number、activate/readback |
| E2E-B5-{1440,1199,390} | B5-01..07：前后记录、relation/subtable/team/comment/history、转交和生命周期 |
| E2E-B11-{1440,1199,390} | B11-01..06：命令 provider、record jump、favorite/recent/quick create/my drafts |
| E2E-BREAKPOINT-{1200,768,767} | 各断点打开 list/detail/form/batch，执行 overflow/axe/focus，不要求重复全部 mutation |

每个 mutation case 保存 request/response、correlationId、SQL 读回和 capability ledger 状态；每个 browser case 统一断言 console error=0、pageerror=0、unexpected failed request=0、axe critical/serious=0、page overflow=0，并保存规定截图。三主视口任何一个核心 case 缺失都不能把对应 capability 标为 ACCEPTED。

## 18. 发布制品证据

- V4 migration 在已固定 digest 的 MySQL 8.0.44/8.4.10 执行空库和 V3.2 升级、组合 FK/互斥 typed value/unique/ordinal 负例、68 张精确表数；8.4 持久卷升级后重启读回。
- generator 提交 20×5 exact-path manifest：Entity `base/entity/{Entity}.java`、Mapper `base/mapper/Module{Entity}Mapper.java`、Service `base/service/I{Entity}Service.java`、Impl `base/service/impl/Module{Entity}ServiceImpl.java`、XML `resources/mapper/base/Module{Entity}Mapper.xml`。Entity/Service 接口保持现有无模块前缀约定，Mapper/XML/Impl 必须 `Module` 前缀。
- generator 在两个全新临时输出根分别生成，exact path、内容 SHA-256 tree digest 必须相同；再写工作树并连续生成两次。四范围报告、legacy duplicate=0、工作树 generated base 精确 340、编译和测试全部通过。
- 完整 Maven tests、frontend unit/build/Playwright、性能/故障/并发报告；任何跳过必须是 Gate blocker。
- backend package 后记录 jar 绝对路径、SHA-256、mtime。按顺序记录 18080 旧端口 owner/PID/命令行，停止旧进程并证明端口释放；使用该精确 jar 启动，60 秒内记录新 PID、完整命令行、进程 start time、18080 owner，按现有 management base path 固定 `GET /management/health` 和 `/management/health/readiness` 均为 200/UP。
- frontend 记录 `dist` 文件 manifest/tree SHA-256、Node/npm 版本和 backend jar hash 对应关系；停止 4173 旧 owner 并证明端口释放，以 `npm run preview -- --host 127.0.0.1 --port 4173` 服务该精确 dist，记录 PID/命令行/start time/port owner。Playwright 只访问 4173 production build，不访问 5173 Vite dev server。
- 停止并重启 backend，清空应用/Redis cache；以普通成员 HTTP 和 SQL 对固定 R100/R103、一个 DRAFT、一个 favorite/recent 读回 schemaVersion、authz epoch、record values/history，保存重启前后摘要；证明没有旧 PID、旧 jar 或内存假持久化。
- evidence `manifest.json` 记录每条命令、退出码、UTC/本地时间、环境、staged tree hash、原始产物 SHA-256、脱敏扫描结果和 reviewer verdict。
- evidence 目录固定 `.cursor/session/evidence/vs4/`；合同评审为 `meetings/S3-vs4-contract-review.md`，开发评审为 `meetings/S3-vs4-review.md`。

## 19. Gate 判定

### 19.1 VS4-001 合同冻结

- product/uiux 对 B3/B4/B5/B11 子能力、异常态、路由和三视口给出 PASS。
- architect/dba/backend/security 对 immutable schema、20 表、49 字段、8 动作、迁移/索引、七 scope、模块边界、事务给出 PASS。
- test/ops 对固定阈值、语料、故障/并发矩阵、浏览器和制品证据给出 PASS。
- PM 记录所有审查意见及处置；无开放 P0/P1 后 Leader 才能将 `contract_status=FROZEN`。否则实现保持 BLOCKED。

### 19.2 VS4 开发验收

- 20 张表/100 个新增 base 文件和所有 migration/generator 证据通过。
- 49 类字段逐项结果与矩阵一致；deferred 能力被服务端阻止且无假 UI。
- B3/B4/B5/B11 的 VS4 子能力全部有真实浏览器、API、DB 读回证据，整体旅程仍标记 PARTIAL。
- 七 scope、字段/动作/敏感、事务故障、并发、性能和重启制品门槛全部通过。
- 所有 reviewer 无开放 P0/P1，Leader 独立复核后才能设置 `developmentBatchAccepted=true`。

## 20. 独立评审待答

1. product/uiux：子能力和 deferred 界面边界是否足以让普通成员完成真实工作，而非原型演示。
2. architect/dba/backend/security：不可变版本、类型迁移、查询模型、授权端口和全事务是否可实现且不形成跨模块污染。
3. test/ops：固定数据集、阈值、故障/并发次数、三视口和精确制品证据是否能阻止局部 CRUD 冒充完成。
4. pm：若任一意见为 BLOCK，必须创建可追踪修订并重新发起相关角色审查，不得自行降级 P1。
5. leader：只在三路 PASS 且证据自洽时冻结合同；框架文件数量、角色描述长度和代码行数都不构成通过理由。
