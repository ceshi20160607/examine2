# unexamine 项目规约

> 目的：沉淀本项目独有的产品、设计、流程规则。通用方法放 `agent-operating-rules.md`，本文件只放 unexamine 项目规则。

## 1. 当前阶段与闸门

- 当前处于设计阶段，`docs/design/user-approval.md` 未签字前禁止进入 API、后端、前端、SQL 开发。
- 每次开工先读 `.cursor/session/state.json`、`docs/user_requirement.md`、`docs/design/prototype-brief.md`、最新 review、`agent-operating-rules.md` 和 `failure-lessons.md`。
- 原型绘制、Open Design 生成、设计评审、开发前扫描开始前，必须先按上述文件重建上下文，相当于“手动压缩到落盘事实”，不允许依赖长聊天记忆直接判断。
- 新的用户纠偏必须沉淀到需求、brief、review、state 或 knowledge，不能只停留在聊天里。
- 开发前多角色原型评审采用 1 小时硬上限：记录开始/截止时间，产品架构、UX、权限流程、开发验收至少四个视角并行或轮询审查；到时必须保存评审结果、已补内容和剩余风险，并停止继续无界讨论。

## 2. 架构壳

- 必须区分平台工作台、平台后台、系统业务页、系统后台四套壳。
- 平台层和系统层不能混在同一导航里。
- 系统业务页参考 CRM 骨架：顶部模块分组，左侧组内模块，右侧列表/详情。
- 系统后台与业务运行态分入口；普通系统成员不能看到后台入口。
- 多系统进入统一使用顶部“系统切换”；个人信息菜单不放“授权系统/进入系统”等重复入口；系统切换必须生成 `SystemSwitchContext`，包含 systemId、tenantId、systemMemberId、effectiveRoles、dataScope 和权限快照。
- 多租户系统必须有 `TenantSwitchContext`，包含 tenantId、tenantRoleIds、tenantDataScope、isTenantSwitchable、disabledReason；单租户隐藏租户切换入口。系统切换或租户切换后必须刷新业务壳品牌、顶部模块分组、左侧模块、列表数据、待办、消息、字段权限和数据范围，不能只跳页面或换标题。
- 系统切换列表必须展示 `accountMemberBindingId`，明确平台账号和系统成员绑定关系。
- 平台账号进入系统前必须有系统成员映射、系统角色和有效数据范围；平台内置超级管理员不能绕过系统成员/权限直接看或改业务数据。
- 平台工作台 Agent、平台消息和平台任务不能直接打开系统业务列表/详情，也不能写入系统业务数据；平台消息中心和系统消息中心必须拆开。必须先通过系统切换取得系统成员、系统角色、字段权限和数据范围，再由系统业务页 Agent、系统消息或业务列表处理。
- 消息模板与通知渠道必须配置化：消息、待办、审批通知、导入导出结果、AI Agent 结果统一由模板驱动，包含 templateCode、层级、变量、发送渠道、跳转目标、去重、已读回执、免打扰、失败重试和 message_delivery_log；平台消息模板不得直接跳系统业务详情。
- 模块组只负责运行态顶部分组、组内模块导航、排序、可见角色和发布关系，不作为业务数据父级；业务数据归属 `systemId/tenantId/moduleId`。
- 对外应用只负责 OpenAPI 接入来源、appKey/secret、scope、回调、限流和审计来源，不作为模块父级，不再用 `appId` 表示业务数据父级。
- 组织树、角色树、模块树、字典分类和待办类型必须能选择节点并刷新右侧列表/配置语义，不能只是静态左栏。

## 3. 消息、待办、列表

- 消息从顶部入口打开右侧抽屉，按手机消息流展示完整内容。
- 关联对象的消息整条点击跳转，卡片内不放“详情 / 查看 / 打开 / 进入”“进入详情 / 打开任务 / 查看日志”等重复按钮。
- 待办是独立工作台：左侧待办类型，右侧待办列表和筛选，不显示业务模块侧栏。
- 明确的数据列表或业务核心列表行点击打开详情；行内按钮只承载不同动作。说明表、配置矩阵和普通状态表不能显示整行可点击手势。

## 4. 工作管理

- 工作管理固定为四个主标签：仪表盘、项目任务、普通任务、日报。
- 仪表盘展示概览、项目/今日预警、当月日历；预警与日历采用上下布局，更清晰。
- 项目任务页只承载项目任务；普通任务页只承载普通任务。
- 项目任务和普通任务都支持“列表 / 看板”切换，二者互斥显示。
- 列表视图必须有分页；看板视图不分页，通过向下滚动自动加载更多数据。
- 看板列、泳道或分组来自工作配置中已发布的下拉单选或多选字段；颜色和图标来自字段绑定的数据字典。
- 日报页只展示“我的日报”列表，并提供筛选项；写日报或自动生成草稿作为操作入口，不在页面常驻大块候选内容。

## 5. 后台配置

- 工作配置必须是系统后台一级页面，不能只藏在模块管理的某一行或抽屉里。
- 工作配置不显示统计卡片，不直接维护状态/标签选项；它配置项目任务字段列表、普通任务字段列表、日报字段列表、字段属性、字段权限、卡片字段、看板取数字段、发布检查和回滚。
- 状态、标签、类型等选项类字段统一绑定数据字典；数据字典负责颜色、图标、语义、排序、默认项、启停、停用历史和引用影响。
- 全系统状态颜色必须统一配置和复用：草稿/待处理为低强调灰，处理中/当前为蓝，待确认/临期为橙，完成/通过为绿，失败/拒绝/逾期/停用为红，自定义标签颜色不能覆盖核心状态语义；颜色配置落在字典项上。
- 模块配置必须覆盖字段、列表、筛选、场景、页面动作、发布检查、权限、导入导出和打印模板。
- 流程配置必须有节点库、画布连线、条件分支标签、属性面板、审批类型、模拟运行、发布检查和影响分析；条件、字段更新、外部 API、超时提醒等节点必须有专属属性承接。
- 后台日志必须在同一个“日志管理”入口内区分登录日志和业务日志；登录日志关注账号安全、认证方式、IP、设备、地点、风险和结果，业务日志关注模块、对象、动作、审批、导入导出、OpenAPI、AI Agent、追踪号和结果。
- 统一认证 / 企业 SSO 不能只在登录页放按钮。平台后台配置管理维护身份源本身：协议参数、证书/JWKS、回调、域名白名单、属性映射、JIT、MFA、测试发布和登录审计；系统后台必须有一级“统一认证”页，配置身份源继承、租户域名、组织部门映射、员工绑定、systemMemberId、JIT 系统成员策略、无成员映射反馈和登录日志 traceId。系统信息页只做摘要和入口。
- 系统 SSO 配置必须和组织架构联动；外部部门要映射系统部门树，externalUserId/email/mobile/employeeNo 要绑定平台账号、系统员工和 systemMemberId，未匹配部门和未绑定员工必须有预检、草稿、人工确认和同步任务。
- SSO、OpenAPI、Webhook、外部模型等密钥必须使用 SecretRef 和版本，不在页面/日志中回显明文；密钥轮换必须形成 SecretRotationJob，覆盖新版本、双写验证、切换生效、停用旧版本、失败回滚和审计。
- OpenAPI 外部应用必须使用 `OpenApiSecretRef`、版本、轮换状态、到期和最近使用表达密钥，不展示明文 appKey/secret。
- 认证成功但没有目标系统 systemMemberId 时，必须形成 NoMemberAccessRequest；审核通过并分配角色/数据范围前，不得进入业务页或自动授权。NoMemberAccessRequest 必须包含 requestId、status、identityProvider、externalUserId、targetSystemId、tenantId、requestRole、approverId、approveResult、roleIds、dataScope、rejectReason、traceId。
- 系统级 AI Agent 策略必须显式保存 `AgentPolicyScope`：moduleScope、fieldScope、actionScope、dataScopeExpression、外发限制、脱敏策略、策略版本。
- 日报自动草稿必须由 `DailyReportAutoSourceRule` 驱动，只读取当前成员有权限的任务、待办、消息、业务日志和审批记录，提交前必须人工确认。
- 关键提交按钮不能落到泛化“操作已响应”：保存草稿、发布检查、审批处理、AI 写入、模板发布、密钥轮换、体检、恢复演练、部署策略等都必须有同步结果、后台任务、发布检查、日志追踪或专属抽屉承接。
- 消息筛选、全部已读、归档、分页上一页/下一页、加载更多、筛选应用、列设置保存、批量处理等按钮必须有可见状态变化或结果承接。
- 关键配置入口不能只用 toast 占位；模块场景、页面动作、发布到业务页、工作字段、工作看板、流程类型、流程模拟、字典发布、AI Agent 发布检查都必须有可开发的抽屉或页面结构。
- 加载、空、无权限、错误、禁用、后台任务状态必须作为统一状态样例沉到原型和 brief，不能留给开发阶段各页面临时补。
- 上线保障必须在原型中可见，而不是只写在需求里：平台配置管理和系统信息页必须表达平台/系统体检、功能开关、灰度发布、容量配额、限流、备份恢复、归档恢复、最近备份、服务连通性和扩容申请。
- 运行态必须提供普通用户日常效率入口：全局搜索、最近访问、快捷创建、表单草稿、错误字段定位；这些入口仍受角色权限、字段脱敏和数据范围控制。
- 流程节点属性、审批处理、AI 写入确认、字典发布检查、AI Agent 发布检查和通用状态样例属于开发前必须专属化的关键模板，不能依赖 `defaultDrawer` 或泛化说明兜底。
- `defaultDrawer` 在本项目中表示设计缺口阻断态，不是开发兜底详情；P0 入口落到 default/generic 时不能进入 coding。
- 平台 Agent 与系统 Agent 的确认页必须分开；平台 Agent 只能确认生成平台任务、平台消息和平台日志，不能复用系统业务写入确认或携带系统业务模块写入 payload。
- 多环境与部署回滚、API 规范与缓存策略属于开发契约级设计入口，平台配置管理和系统信息页必须可见表达，不能只放到需求或 API 阶段再补。
- 收藏菜单、移动端扫码和拍照上传属于普通用户日常效率能力，必须进入运行态快捷创建/附件草稿链路，并受权限、脱敏、失败重试和业务日志控制。

## 6. 交付前检查

- 原型必须能走通：登录、注册/找回、系统业务、系统后台、平台工作台、平台后台、消息、待办、工作管理。
- 本项目最终打包不能只交付 `examine-web-*.jar`。必须生成 `release/unexamine-<version>/` 和 zip，后端目录保持简单：`examine-web.jar`、`application.yml`、`server.sh` 同级，通过 `server.sh start|stop|restart|status|health` 承接启停状态，另包含 `logs/`、`data/uploads/` 和前端静态包；验收时必须用发布包脚本启动服务，并验证外置端口配置、健康检查、重启和停止端口释放。
- `server.sh start` 不能只判断进程存在，必须等待 `/api/v1/health` 中 `status/database/schema/redis` 全部为 `UP`；Redis 是登录 token 会话存储，Redis DOWN 时发布启动必须失败并打印健康响应和日志。
- 注册创建系统后必须有系统初始化引导，至少覆盖系统信息、组织架构、角色权限、模块字段、流程字典、邀请成员和发布前检查；不能只跳到后台首页。
- 平台 Flow、有效权限计算、流程高级运行策略属于开发契约级入口，原型必须提供专属结构和字段样例，不能用通用详情或说明抽屉替代。
- 每次给用户看之前检查：重复按钮、重复导航、列表行点击、页签目标、抽屉目标、权限入口、筛选和分页；操作列还要扫描“详情 / 查看 / 打开 / 进入”同义详情按钮，确认它们不是和整行点击重复。
- 设计仍未获签字时，最终说明必须强调未进入开发。

- 开发前覆盖锁定要求：brief 中的关键产品词必须能在原型中找到可见证据。对会影响 API、任务拆分、权限、筛选、状态、运维边界的能力，不接受只用近义词或泛描述暗示；必须显式写出系统生命周期、字典类型、消息筛选、平台级模型/系统级 Agent、容量配额、备份恢复、归档恢复、错误状态和禁用状态等开发可抽取名称。
- 开发前若多次复审仍发现明显问题，必须采用干净上下文复审：审阅者只读本项目落盘文件和当前原型，至少覆盖产品架构、UX、权限数据、QA/开发验收四个视角，结论写回 review 和 readiness 后才允许继续给用户确认。

## 7. Local Build Environment Correction

- The earlier `D:\Tools\...` and `D:\java\...` JDK/Maven/npm paths are not valid on the current machine.
- Current verified JDK 21 path is `D:\dev\jdk21-temurin`.
- `D:\dev\jdk21` / Oracle 21.0.10 triggers javac internal `StackMapTableFrame.getInstance` failures in this project and must not be used for verification.
- Current verified Maven path is `D:\dev\maven\bin\mvn.cmd`.
- Current verified npm path is `D:\dev\nodejs24\npm.cmd`.
- Before Java compilation, set `JAVA_HOME=D:\dev\jdk21-temurin`; do not rely on stale global environment variables.
- Do not report backend verification until Maven itself reports Java 21 in `mvn -version`.

## 8. Real Deployment Default Account

- The real deployable platform root account is `admin` with initial password `123123aa`.
- The real platform root role code returned to frontend is `PLATFORM_ROOT`.
- Historical prototype fixture names such as `platform_admin_root` are not the deployable login account.
- Backend startup must bootstrap this account and role idempotently through configurable `unexamine.bootstrap.platform-root.*` settings.
- The platform root account only owns platform backend authority by default; it must still create or switch into a system to receive a system member context before accessing system business data.

## 9. Flow Canvas Runtime Rule

- Flow creation must not silently inject demo/sample nodes, edges, modules, fields, external apps, or message templates.
- A new flow without `canvas` is a draft with an empty canvas; publish must fail with a business validation error until real nodes and edges are saved.
- E2E tests that publish a flow must submit an explicit canvas in the request, so approval runtime evidence never depends on hidden sample data.

## 10. Frontend Runtime Data Rule

- Production frontend pages must not fall back to prototype-specific local records, module names, import/export files, or domain fixtures when an API call fails.
- Runtime business lists must render backend schema columns and backend record fields dynamically; unavailable data must show loading, empty, no-permission, or error states.
- Demo fixtures may only live in explicit test/fixture files and must not be imported by production route components.

## 11. Final Orchestration Integrity Rule

- Final release orchestration must parse child machine-readable output and fatal text. A wrapper process status is not sufficient when a child script reports `status=FAIL`, backend startup failure, release verification failure, native command failure, Node audit failure, or API failure.
- Release packaging must hash-verify copied artifacts, including `examine-web.jar`, and fail if the release artifact is not byte-identical to the build output.

## 12. Final Usable System Framework Rule

- 本项目的最终目标不是“原型页存在”或“R 批次通过”，而是一个真实用户可以使用的无代码定制系统。
- 当前最终目标总账入口是 `docs/recovery/final-usable-system-acceptance.md`；框架级规则入口是 `.cursor/architecture/final-goal-framework.md`。
- 后续任何修复都必须先判断影响哪个角色旅程：平台管理员、平台普通成员、系统管理员、系统普通成员、外部系统或运维人员。
- P0 任务卡必须链接到最终旅程门，覆盖前端、后端、数据、权限、状态、读回、发布/运维证据。
- 生成接口只算基础设施；无代码平台的真实能力必须落到编码业务服务、运行态页面、权限、审计、发布检查和可重复脚本。
- 用户签字前只能说“工程证据支持”，不能说“最终目标已经完成”。

## 13. Deferred Framework Extraction Rule

- 未来可复用框架抽取不是当前执行主线；当前主线是把本项目做到真实可用并获得用户认可。
- 当前不整理跨项目抽取包，不规划未来项目适配，不为复用而改动产品边界。
- 只有当某个框架改进能直接推动当前系统可用时，才落到 `.cursor/architecture`、`.cursor/templates` 或 `.cursor/workflows`。
- 本项目完成并验收后，才重新讨论可复用框架抽取。
## 14. Clean Rebuild Product Taxonomy Rule

- 当前主线是 clean rebuild：旧实现位于 `.oldbk/restart-20260708-220451/`，只读参考，不作为完成证据。
- 顶层导航按上下文运行：平台上下文显示平台统计和平台对象；系统上下文显示当前系统统计、业务模块和系统对象。
- `工作台` 是上下文统计和仪表盘，不是固定平台首页；后台可配置平台/系统统计口径。
- `Flow` 是流程和服务编排大模块，可关联系统内自建业务模块，也可关联外部第三方系统；对外提供能力时必须经过 `应用` 授权。
- `应用` 是系统内应用通信和对外服务暴露的授权阀门，不是业务模块父级，也不是系统切换入口；Flow 或系统服务对外提供服务时，必须先创建应用并关联授权范围。
- `工作` 固定承载工作统计、项目任务、普通任务、日报；不得拆成互相孤立的小菜单。
- `AI` 是用户使用 AI 解析和辅助系统扩展的入口；模型、策略、权限、脱敏和审计配置都在后台管理。
- `待办` 是配置驱动的动作/提醒工作台，包括提前提醒、待我审批、今日需我回复等固定场景；场景来自后台配置或固定列举，不从业务模块侧栏派生。
- `消息` 是与当前用户相关的消息流，包括 @、审核、抄送、导入导出和主要操作提醒；整条消息可跳转，卡片内不重复堆“查看/打开/进入”。
- `后台` 是当前上下文的管理入口，覆盖基础信息、组织、角色、模块、流程、应用、仪表盘、字典、工作配置、应用配置、AI 配置、数据源和日志。
- `个人信息` 只承载个人资料、系统切换、退出；不得再放重复系统入口。
- `业务模块` 以配置的系统内业务模块组作为标签页；组下模块必须覆盖字段、动作、页面、打印模板和运行态权限。
- `flow管理` 必须覆盖流程图、业务模块/外部系统关联、审批人、数据源、审批节点、动作、发布检查和运行读回。
- 列表主界面采用左侧标签页/左侧分类；详情采用右侧标签页/右侧工作区；行点击打开详情，行内按钮只做差异动作。

## 15. Requirements Rebuild And Engineering Governance Rule

- 当前阶段不是开发，是重新整理需求和完善工程。禁止继续按旧 `REBUILD-P0-006` 进入 Flow/Application 编码。
- 后续所有工作必须先按工程架构组织：产品域、后端模块、前端壳和路由、表前缀、权限边界、状态流转、消息/待办/日志副作用、验收证据必须能互相映射。
- 需求、参考原型、流程图和任务拆分阶段必须尽量发现产品边界、工程架构、权限数据、角色旅程和验收口径问题；不能等编码时发现一个就临时改一次。
- 如果编码阶段暴露出上述结构性问题，必须停止当前实现，走组委会式复审。组委会至少覆盖产品、架构、后端、前端、DBA、测试视角，结论写回 `.cursor` 或 retained source 派生文件后再继续。
- 组委会不是小缺陷处理流程；小文案、小样式、小字段遗漏可按任务修复，但影响产品边界、数据模型、权限模型、流程闭环、任务拆分或验收标准的问题必须走组委会。
- 遗留文件处理必须先盘点分类：retained source、reference implementation、evidence archive、generated/cache noise、deletion candidate、unknown。没有盘点和影响说明，不得删除。
- 有参考价值的旧实现只总结模式和目录经验，不作为当前完成证据；没有参考价值的遗留物要写清删除原因和影响后再删；不确定的先保留。
- 后端编码恢复时必须遵循 `.cursor/architecture/backend-structure.md`：先由代码生成工具生成 `base` 基础代码，再在 `manage` 下写业务逻辑。
- `base` 层只放 generator 产出的 entity、mapper、mapper.xml、IService、ServiceImpl 等贴表基础代码；不要手写大批量 base CRUD。
- `manage` 层承接 controller、业务 service、BO/DTO、VO、权限、事务编排、读回、消息/待办/日志副作用和业务语义。
- 生成 CRUD、静态页面、接口 200、构建通过都只是局部证据，不能证明产品可用。
- UI/功能复审已升级为工程框架：后续用户可见任务必须遵守 `.cursor/architecture/ui-system.md` 和 `.cursor/session/rebuild/ui-system-interaction-contract.md`，按深色顶栏、高密度列表、右侧详情工作区、清晰状态与读回证据实现；旧 `19999` 产物只能作为失败参考，不能作为目标样式或完成证据。

## 16. Encoding And Records Rule

- 活跃项目文本文件统一 UTF-8；`.editorconfig` 是编码规则入口。
- Windows PowerShell 读取或写入项目文本时必须显式使用 `-Encoding UTF8`，避免把 UTF-8 无 BOM 文件误读成乱码。
- 看到乱码时先判断是文件内容损坏还是终端/命令解码错误；确认后再修复，不能盲目重写大文件。
- 新增或修改治理、需求、设计、任务、源码文件后，必须扫描常见 mojibake 标记；命中后先处理编码问题再继续验收。
- 确定性会议纪要统一维护在 `.cursor/session/project-minutes.md`，只记录已经确定的日期、主题、结论、来源和影响。
- 未确定事项不写入会议纪要，保留在对应文件的 Open Decisions、Open Questions、Next Work 或任务阻断项中。
- 临时文件不得成为事实来源；需要跨任务保留时，必须登记到 `.cursor/session/project-minutes.md` 的 Temporary File Ledger，并写清用途、处置规则和 owner。

## 17. Leader Three-Cycle Governance Rule

- 用户明确要求 leader 组织相关人持续推进时，不得把普通继续推进问题反复抛回给用户。
- leader 必须组织产品、架构、后端、前端、DBA、测试/QA 等相关视角，完成复审、整理、处理、决策、补充和再验证。
- 大范围治理/重构整理采用三轮内部闭环：第 1 轮角色复审，第 2 轮落盘集成，第 3 轮一致性验证。
- 每轮只把确定结论写入 `.cursor/session/project-minutes.md`；未确定项留在对应契约的 Open Decisions 或任务阻断项。
- 三轮内可自行继续，不需要用户逐句确认。只有最终签收、真实用户偏好选择、外部事实无法从项目文件推断时，才向用户请求裁决。
