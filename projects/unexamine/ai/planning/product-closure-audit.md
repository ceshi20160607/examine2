# 当前产品全局收口对账（2026-10-16）

## 判断边界

唯一产品需求源是 [`docs/user_requirement.md`](../../../../docs/user_requirement.md)。本文只记录当前源码、入口和验收结果的对应关系，不新增产品规则，也不把步骤数量换算成完成率。S18.29–S18.38 核对了旧打印过渡、升级兼容、模块动作、同一模块业务旅程，以及条件和并行 Flow；尚未重新执行整站业务旅程。`execution-context` 与 `workspace-validate` 均通过，步骤回执不等于整体产品验收。

状态含义：**场景已验**表示链接回执证明了所写的具体场景；**局部**表示入口或实现存在，也有部分场景证据，但不能覆盖本行全部要求；**待核**表示尚未找到足以判断完整结果的当前验收，不等于代码不存在；**缺实现**表示已找到明确的代码限制；**回归**表示已有反例；**缺交付**表示仓库没有发现要求的最终交付物。旧回执在后续代码或需求口径改变后，须复验受影响结果。

## 整体结论

本次复核纠正执行优先级：S18.37 的条件分支和 S18.38 的并行汇聚均有各自场景回执，但继续做 S18.39 包容网关，不能回答第 2.3 节的管理员/普通用户完整旅程和第 10 章的全量交付。S18.39 保留为未验需求，不作为当前执行入口；先建立整站发布关口，按同一制品、空库新装、两个系统和同系统多租户贯通业务，再据实际阻断决定修复顺序。

| 发布关口 | 2026-10-15 核对结果 | 处理判据 |
| --- | --- | --- |
| 产品主线 | G1–G17 各有指定场景回执；S19.1 在 V23 空库的同一候选制品上贯通两个系统、次租户、桌面与 390px 手机核心旅程，并完成重启读回。此项只覆盖选定业务路径，不能推断全部功能域或第 10 章交付完成。 | 以 S19.1 回执为当前核心路径证据；继续核对交付包、手册、恢复及剩余需求域。 |
| 需求追溯 | 台账 2087 条原文行中，2041 条仍为 `unverified`；这不能换算为功能缺失率，但说明 17 个局部关口不能替代全量需求签收。 | 对每个需求域标注已验、局部、缺实现或不适用，并指向当前版本可重放证据。 |
| 身份源 | S19.3–S19.6 已验本地 OAuth2、签名 SAML2、LDAPS、OIDC 协议夹具及系统成员准入审计。第 5.1 节写有 JIT 和企业身份源配置，但企业 SSO 按需配置，未配置时不要求真实 IdP 凭证。 | 已启用的身份源按其实际配置验收；未配置的外部身份源、渠道及真实 IdP 联调不单独阻断核心交付。JIT 仍是已写明但尚未验收的条件能力。 |
| 信息架构与测试 | 当前前端构建通过，41 个文件的 172 项测试全部通过；系统后台实际页面显示 11 项一级能力及“其他业务参数”。模板配置另有独立“导出配置”页，须按第 4.4 节的模块动作边界检查是否形成重复配置入口。 | 继续对管理员和普通成员导航做完整页面走查，确认配置边界与运行入口一致。 |
| 交付物 | 当前候选包 `0.1.0-s19.9-dev1` 包含 JAR、Node 前端、逐文件摘要、数据库脚本、启停/备份/恢复脚本及独立使用说明；复用已有 Docker MySQL/Redis 完成 V23 首装、更新、浏览器登录、备份、回切及当前包另库恢复。S19.2 的另库恢复证据属于旧包。 | 当前包仍是隔离环境候选制品；生产环境配置、真实外部依赖及全量功能签收仍须按第 10 章完成。 |
| 历史打印 | 需求要求模板改版后历史单据可按历史模板打印；既有 G7、S18.29 场景证据须在当前候选上核对。隔离测试库部分旧打印作业缺少文件字节，仅说明该测试夹具不完整。 | 用有完整单据和模板数据的夹具复验历史模板打印；不把找回旧测试库中不存在的 PDF 字节列为产品交付条件。 |

核心业务旅程、实际启用能力、需求追溯和最终交付尚未全量验收，整站状态仍为**未验收、不可交付**；未配置渠道和缺失旧测试文件本身不是阻断项。前端构建的大包警告已记录，待功能关口后结合第 14.29 节实际加载和响应测量处理。

S19.1 在同一 V23 空库候选环境用 Edge 创建两个系统和次租户，发布“客户记录”“订单记录”及目标系统的“服务合同”。普通成员在桌面与 390px 手机完成客户记录新增、编辑、列表和详情；审批人在桌面和手机办理同一客户模块的审批。成员在两端打开附件、打印预览和导出入口，桌面实际生成 PDF 和 CSV；API、数据库及文件字节读回一致。目标系统应用只返回获权字段、启动获权 Flow，越权写入、越界目标系统和非应用身份请求均被拒绝；停用应用后调用被拒绝。次租户成员只看到本租户记录，主租户记录互不可见。两次后端重启后的页面和持久化读回通过。[S19.1 回执](../evidence/s19-product-release-verification.json)记录具体覆盖范围；现有账号的临时密码恢复仅为隔离环境续验手段，不计产品找回密码验收。交付物、其他身份源、历史打印字节与全量需求追溯仍未闭合，整体结论不变。

S19.2 在独立安装目录由空库执行部署脚本，首次迁移 V23 后因缺少默认管理员初始化密钥退出；部署脚本留下失败记录并停止服务。补齐必填配置示例与独立环境密钥后，同库按前向修复启动，随后用新清单版本更新；Edge 经 Nginx 代理登录并创建系统，重启后仍能进入。备份结果的数据库、文件、配置和密钥引用四件制品摘要一致；恢复到新数据库后，191 张总表、V23、系统记录与文件标记字节均与源端一致。[S19.2 读回](../evidence/s19-package-backup-restore.json)及[安装失败记录](../evidence/s19-package-install-failure.json)保留实际边界。文件标记只验证恢复机制，不替代历史业务 PDF 的配套字节验收。

S19.3 在另一个 V23 隔离库发布 `0.1.0-s19.3-dev2`，通过平台预检与发布、系统部门/账号/成员映射预检和人工确认，使通用 OAuth2 授权码登录从浏览器身份源页返回平台并进入获权系统；无权系统返回 403。先前空库启动因本机 Java 路径和新库授权失败，失败原因记入本步回执，同库修复后首装和同结构更新通过。最终包重启后再次完成 Edge 登录，数据库读回身份源版本、身份绑定、系统绑定与登录审计；OIDC/OAuth2 后端集成测试覆盖授权码、PKCE、无效或过期状态、重放、未验证邮箱、越界域名和缺失绑定，前端构建及 172/172 测试通过。[S19.3 浏览器与数据库回执](../evidence/s19-oauth2-browser.json)只证明本地协议夹具，不代表真实企业 IdP、JIT 或全量第 5.1 节已签收。

S19.4 在新的 V23 隔离库发布 `0.1.0-s19.4-dev1`。平台管理员通过预检解析 IdP X.509 证书并发布 SAML2；系统管理员将外部身份经部门/员工/成员预检和人工确认绑定到既有账号。Edge 从发布入口进入本地 IdP，接收签名 HTTP POST Response，由后端 ACS 校验请求关联、受众、接收地址、证书、时效和签名后交付一次性码，进入平台及获权系统；无权系统 403。Testcontainers 用例覆盖签名篡改、错误请求/受众、断言过期、ACS/交接重放、域名不符、邮箱未验证、未映射账号和过期状态。包重启后再次完成浏览器登录；[页面读回](../evidence/s19-saml-browser.json)和[数据库读回](../evidence/s19-saml-db-readback.json)仅证明本地签名协议夹具。当前不支持要求 SP 请求签名、只签 Assertion、加密断言或 IdP 主动登录的身份源；真实企业 IdP 仍需单独联调。

S19.5 复用本机正在运行的 MySQL 与 Redis，只在 MySQL 新建 `unexamine_s19_ldap` 隔离库；前后端直接连接，没有另建 MySQL、Redis 或 Nginx。V23 空库首装后，本机 LDAPS 夹具的可信证书、服务账号 Bind 和搜索基准预检通过，草稿隐藏且发布后登录入口可见；系统管理员预检并确认既有账号/成员映射。Edge 填写目录凭据进入平台与获权系统，后台重启后再次成功。错误密码、缺失/冲突目录身份、域名越界、缺映射及无权系统被拒绝，未受信任证书和明文 LDAP 预检失败；[API 回执](../evidence/s19-ldap-api.json)、[浏览器](../evidence/s19-ldap-browser.json)、[数据库](../evidence/s19-ldap-db-readback.json)记录具体范围。此验收使用临时本地目录证书，没有真实企业 LDAP/AD 联调，也没有验证 AD 特有账号格式或目录策略。

S19.6 在同一个后端 JAR、前端构建及现有 Docker MySQL/Redis 上，把平台 SSO_LOGIN 关联到服务端会话；系统进入时写独立、不可变的成功或拒绝事件，保留身份源、外部用户、账号与成员结果、认证方式、MFA、IP/设备和两次请求的追踪号。LDAP 成功、无成员系统、停用成员和租户切换拒绝均由 API 与系统后台读回；OIDC、OAuth2、签名 SAML2 的本地协议夹具各自完成回调、获权进入和同一审计合同。[API](../evidence/s19-unified-sso-api.json)、[协议回归](../evidence/s19-unified-sso-shared-protocols.json)、[页面](../evidence/s19-unified-sso-browser.json)和[重启后数据库](../evidence/s19-unified-sso-db-readback.json)记录边界。平台会话尚未选择系统时不虚报成员匹配，本地登录不误记企业来源，审计不保存目录密码。G18 仍未完成；S19.7 已将当前交付包改为单前端、单后端并复用现有 MySQL/Redis，不增加 Nginx。

S19.7 使用现有 `mysql`、`redis` 容器，在独立 `unexamine_s19_direct` 库完成 V23 首装，发布包只启动一个 Java 后端和一个 Node 前端；Edge 从同一前端端口登录、进入新系统，`/api` 与健康检查由前端进程转发。停启、同版更新、按原版本回切及最终候选包重部署后，系统记录仍在；容器始终只有原有两个。直接使用容器内 MySQL 客户端生成四件备份制品，哈希、191 张总表和系统 SQL 读回一致。篡改前端服务脚本或把 JDBC 端口指向非所选容器会在切换前拒绝且两个应用进程继续运行。[浏览器](../evidence/s19-direct-deployment-browser.json)、[数据库与进程](../evidence/s19-direct-deployment-readback.json)、[失败路径](../evidence/s19-direct-deployment-port-denial.json)及[回执](../evidence/s19-direct-deployment-verification.json)记录范围。旧 S19.2 的 Nginx/附带依赖包只保留历史证据；现行发布入口以 S19.9 包为准。真实生产环境、历史打印字节和全量产品需求仍未签收。

S19.8 从当前直连包生成含真实文件标记字节的数据库、文件、配置和密钥引用备份，复用现有 `mysql` 容器恢复到新隔离库；源库和目标库均为 Flyway V23、191 张总表及一条系统记录，文件 SHA-256 一致，四件制品摘要和仅引用、恢复时轮换的密钥策略通过。首次导入后因空导入日志写入错误留下失败记录和隔离库，修复后换新库完成演练；当时 `0.1.0-s19.8-dev1` 包的恢复结果以最终新库为准。已存在目标库、错误容器端口、篡改配置备份均被拒绝且不影响源库。[数据与文件读回](../evidence/s19-direct-restore-readback.json)、[失败路径](../evidence/s19-direct-restore-denial.json)、[包与进程读回](../evidence/s19-direct-restore-package-readback.json)记录范围。文件标记只证明备份恢复机制，不代表历史 PDF 字节签收；当前候选包仍不能代替生产环境和全量业务验收。

S19.9 按用户新增的端口要求，将后端默认值、部署配置示例、制包清单、前端开发代理和直连前端代理统一到 `9999`；发布、回切和启停预检拒绝旧端口配置或旧清单。`0.1.0-s19.9-dev1` 在现有 `mysql`、`redis` 容器及隔离源库上发布后，后端监听 9999、前端仍监听 15173，旧 18087 已关闭；前端健康检查和受保护 API 均经新后端读回，V23 和已有系统记录保留。新包又完成四件备份及新隔离库恢复，191 张总表、系统记录及文件标记字节一致。[端口和进程](../evidence/s19-fixed-backend-port-readback.json)、[旧配置拒绝](../evidence/s19-fixed-backend-port-denial.json)及[同包恢复](../evidence/s19-fixed-backend-port-recovery.json)记录实际范围。实际启用的企业身份源与 JIT 仍须按配置验收；未配置渠道和旧测试库缺失的 PDF 字节不阻断核心交付，全量追溯仍未闭合。

S19.11 当前仍在执行。`0.1.0-s19.11-dev3` 在现有 `mysql`、`redis` 容器上的新 V23 空库完成两个系统、两个模块组及客户/订单模块页面发布；普通成员、审批人、无权账号、次租户及跨系统应用完成选定桌面和 390px 手机旅程。实际上传附件、打印 PDF、导出一行 CSV，邀请由管理员页面生成并由受邀账号接受；普通成员可搜索、查看三条真实消息。四件备份在另一个新数据库恢复出 191 张表、五条记录、三个完成的 Flow 实例与相同文件字节；同包重启后 API、MySQL 与文件读回通过。隔离重放还发现并修复共用 Redis 时按数字 ID 复用旧库权限缓存和后台作业键的问题。管理员随后从页面发布角色、审批 Flow 与绑定、模块动作和导出配置，完成真实 CSV 导入、个人文件中心字节读回、导入审计查询和运维依赖重检。[dev4 包读回](../evidence/s19-core-dev4-package-readback.json)确认修正手册已入包、同库更新成功。dev4 又从页面发布[平台公共首页](../evidence/s19-core-dev4-platform-dashboard-browser.json)和[系统客户首页](../evidence/s19-core-dev4-system-dashboard-browser.json)。平台应用内部调用暴露平台会话无 `memberId` 却按它查询来源成员的缺陷；dev5 改按平台与账号查有效成员，HTTP 回归 2/2 通过。[dev5 应用页面与调用](../evidence/s19-core-dev5-platform-app-browser.json)成功创建平台 Flow 草稿，未授权动作 403；仪表盘和运维页同包读回通过。发布包只启动一个后端、一个前端，后端端口 9999；[进度回执](../evidence/s19-core-release-progress.json)列出边界。[dev5 同包恢复与重启](../evidence/s19-core-dev5-recovery-readback.json)再次通过 V23、191 张表、四件制品及平台/系统首页读回；[第 10 章逐项矩阵](../evidence/s19-core-chapter10-matrix.json)列出已验路径和余项。新 Flow 版本实例对照、第 10 章其他功能域、包内手册由新操作员逐步操作仍未全量签收。G18 仍未完成，不从本次选定旅程推断整站交付。

dev5 后续又完成个人文件跨系统拒绝、导入安全回滚与仪表盘重新计数、平台应用外部签名成功/拒绝，以及[管理员页面撤权与恢复](../evidence/s19-core-dev5-admin-role-revocation-browser.json)：原受邀成员的消息权限预览随版本 12→13→14 依次为允许、拒绝、允许。该成员当前候选库的登录口令尚未取得，其失败登录尝试不计验收；另一个新注册并受邀的账号随后完成独立登录的运行态允许/拒绝/恢复验证。

同一候选中，平台应用的旧签名凭证在页面轮换后返回 `APPLICATION_CREDENTIAL_INACTIVE`，新凭证仍有效。[系统应用 V3 重启读回](../evidence/s19-core-dev5-system-app-restart-readback.json)证明目标系统页面把原有三项授权发布为内部与外部双通道后，签名列表/详情只返回授权字段，越权动作与目标范围被拒绝；来源系统内部通道在发布与重启后仍返回目标记录。完整应用版本和第 10 章其他功能域仍按[矩阵](../evidence/s19-core-chapter10-matrix.json)逐项验收。

2026-10-17 同一 dev5 候选新增三段可重放结果：[受邀账号三次独立登录](../evidence/s19-core-dev5-fresh-role-runtime-browser.json)核对角色授予、页面撤销、页面恢复后消息 API 的 200/403/200；[平台申请访问](../evidence/s19-core-dev5-access-request-browser.json)从无权进入 403，经页面提交和系统后台批准，到重新登录进入，调试留下的两条申请已[填写原因拒绝](../evidence/s19-core-dev5-access-rejection-browser.json)；[系统应用版本回滚和恢复](../evidence/s19-core-dev5-system-app-rollback-browser.json)使外部签名 LIST 200→403→200，内部 LIST 始终 200。后者恢复后重新发布为新版本，原历史快照保留。平台后台其他路径、平台应用版本、手册逐步重放和全域签收仍未完成，G18 不改为完成。

同日平台后台走查确认了实质缺口：[dev5 页面证据](../evidence/s19-core-dev5-platform-backend-inspection.json)显示“组织架构”“角色权限”“数据字典”“审计日志”四个入口都只出现配置键和值编辑器。dev7 已把审计入口改为真实平台事件查询与详情，按 `platform_id` 和上下文隔离；V24 在唯一平台的候选库补齐 227 条旧事件的平台归属。首次 dev6 迁移误写表名，Flyway 留一条失败历史但未更新事件；核对后只清理该失败记录，修正 SQL、重制不可变 dev7 包，复用升级前四件备份完成 V23→V24，191 张表仍在。[平台审计页面与拒绝](../evidence/s19-core-dev7-platform-audit-browser.json)验证筛选、详情、系统事件 404、无权账号及系统会话 403；[系统审计](../evidence/s19-core-dev7-system-audit-browser.json)和内部应用 LIST 继续可用。审计子路径已修复，平台组织、平台角色与平台字典仍缺业务管理页面/服务，属于已证实的第 10 章阻断，而非单纯未测试。[升级回执](../evidence/s19-core-dev7-platform-audit-upgrade.json)记载数据修复和失败处理；全站状态不变。

后续 dev9/10 已修复上述平台组织和平台角色两个通用键值入口的选定业务路径：[组织页面](../evidence/s19-core-dev9-platform-organization-browser.json)建立、更名平台部门并调整成员归属，保留原有平台角色；[角色页面](../evidence/s19-core-dev10-platform-authorization-browser.json)建立角色、发布平台动作授权并分配成员，按新成员身份核对允许、拒绝与撤权即时生效，内置管理员及无效通配授权受到保护。[dev10 同包与数据库回执](../evidence/s19-core-dev10-platform-authorization-upgrade.json)显示 V24、191 表、后端 9999、单前后端、已有 MySQL/Redis。平台字典仍为已确认的通用键值编辑缺口；组织停用和引用处理、其他第 10 章功能域与手册逐步验收仍未完成，整站不改为可交付。

dev11 修复了共用配置路由误用“配置管理”权限的页面入口；[角色只读成员实测](../evidence/s19-core-dev11-platform-authorization-browser.json)可进入真实角色页，不能经导航或直接 URL 打开组织页，撤权后的 API 403 与页面一致。[同包回执](../evidence/s19-core-dev11-platform-category-route.json)记录 V24、后端 9999 和相同后端 JAR。平台字典仍只有键值编辑器，独立归属、发布快照和实际引用影响的[实现合同](platform-dictionary-contract.md)已锁定；需求未写明跨系统字段绑定，不将它伪装成本轮要求。第 10 章完整签收仍未完成。

- 主干已经成形：统一账号、系统与租户、模块元数据和通用记录、Flow、应用、仪表盘等均有页面和后端入口；S01 空库验证时的 188 张表使用 `un_` 命名，[当时的空库和 Base 验证](../evidence/s01-unified-schema-verification.json)已记录；当前候选库 Flyway V23 为 191 张表。G1–G17 各有指定业务场景回执。
- **当前不能交付**：项目状态的 `productCompletion` 为 `not_verified`，G18 仍未完成。依据只有当前需求与当前候选包的整站验收结果；旧隔离库的打印文件字节问题不作为需求或本次发布阻断项。
- 模块功能仍未封口：S18.31 验证了新模块审批动作；S18.32 验证了新旧模块的仪表盘动作。S18.33 在隔离库实际完成 V22→V23 升级，旧发布首页 #9 对原 LIST 成员继续可见，新发布首页 #10 仅对有 DASHBOARD 权限的所有者可见；停用与回滚、Edge 导航、新装集成测试和新 JVM 读回均通过，历史模块/首页快照、草稿、记录和打印任务未被迁移改写。该兼容只覆盖迁移时固定的已发布页面。S18.34 在同一模块 #94 上组合发布打印、审批和首页，PDF #134、Flow #53、首页 #8 与动作停用/回滚互不破坏；无权成员的五个入口均拒绝，Edge 看到三个入口并进入首页，新 JVM 读回通过。S18.35 又在 #94 导入记录 #188、导出筛选结果并转移负责人；无效导入行及无效转移目标被拒绝，导入回滚因后续转移产生版本冲突而保留记录，Edge 和另一 JVM 均读回，旧 PDF/Flow/首页未变。S18.36 将 #94 的已发布转化映射到 #93 当前必填字段，生成关系 #4 和目标 #189；重复/跨系统转化被拒绝，来源与目标后续编辑互不改写，分别删除来源或目标均保留另一方和历史关系；Edge 跳转、独立 JVM 和新装库集成测试通过。审批其他节点和复杂条件及全产品组合仍需整体验收。[当前执行状态](spark-state.json)保留下一项确切范围。
- **按配置验收的能力**：[发布前预检](../../backend/src/main/java/com/unique/unexamine/authentication/manage/SsoProviderAdministrationService.java)及[运行登录](../../backend/src/main/java/com/unique/unexamine/authentication/manage/EnterpriseSsoService.java)已有 OIDC、OAuth2、SAML2 和 LDAP 本地协议路径；AD 特定行为、企业微信/钉钉及 JIT 尚无当前候选验收。系统成员结果由映射日志与进入日志分别追踪。仅在实际配置相应身份源或启用 JIT 时，将其作为该配置的验收条件；未配置时不要求真实企业 IdP 联调。
- [第 10 章](../../../../docs/user_requirement.md#10-全量交付标准)要求的管理员、用户、应用接入、打印和排错说明已形成[独立文档](../../deploy/guides/README.md)，版本化候选 ZIP 已在隔离环境完成首装、更新、备份和恢复。真实生产环境与未覆盖功能域尚未签收，不能把本地候选包当作最终交付。
- 需求台账的 2087 条是**原文行索引**，其中 2041 条标为 `unverified`，不是 2041 个缺失功能，也不是完成率。A.15–A.19 对应行尚无 `verified` 标记；部分场景实际有回执，说明全量追溯本身仍未闭合。

## 全量功能域对账

| 需求 | 当前入口与可复核证据 | 判断和未闭合处 |
| --- | --- | --- |
| [5.1 账号与登录](../../../../docs/user_requirement.md#51-账号和登录) | [认证页](../../frontend/src/product/views/AuthView.vue)、[认证接口](../../backend/src/main/java/com/unique/unexamine/authentication/manage/AuthenticationController.java)、[SSO 配置页](../../frontend/src/product/views/PlatformSsoProvidersView.vue)，[G1](../evidence/g1-identity-live-verification.json)、[S19.3](../evidence/s19-oauth2-identity-verification.json)、[S19.4](../evidence/s19-saml-browser.json)、[S19.5](../evidence/s19-ldap-browser.json)、[S19.6](../evidence/s19-unified-sso-db-readback.json) | **局部**：账号建系统旅程和四种本地协议的统一成员准入审计有证据。按需身份源的 JIT、AD 特定行为、企业微信/钉钉尚无当前候选验收；未配置外部身份源时不把真实 IdP 凭证作为核心交付条件。 |
| [5.2 系统和租户](../../../../docs/user_requirement.md#52-系统和租户) | [系统入口](../../backend/src/main/java/com/unique/unexamine/system/manage/SystemController.java)、[租户扩展页](../../frontend/src/product/views/TenantExtensionsView.vue)，[G1](../evidence/g1-identity-live-verification.json)、[S18.14](../evidence/s18-tenant-dashboard-isolation-verification.json) | **局部**：建系统、默认主租户和仪表盘租户隔离有场景证据；模式切换、扩展、共享及会话失效的完整组合尚无当前封板。 |
| [5.3 应用](../../../../docs/user_requirement.md#53-应用) | [平台应用页](../../frontend/src/product/views/PlatformApplicationConfigurationView.vue)、[系统应用页](../../frontend/src/product/views/ApplicationManagementView.vue)，[跨系统](../evidence/s05-cross-system-application-verification.json)、[版本](../evidence/s18-app-version-verification.json)、[调用策略](../evidence/s18-app-call-policy-verification.json) | **局部**：内外访问、授权、版本、安全策略有分场景结果；必须在最终整条链中复验。应用仍应是访问桥梁，不成为模块父级。 |
| [5.3.1 模块组](../../../../docs/user_requirement.md#531-模块组v2-新增运行态导航) | [模块配置页](../../frontend/src/product/views/ModuleConfigurationView.vue)、[运行页](../../frontend/src/product/views/RuntimeWorkspaceView.vue)，[多组导航](../evidence/s18-multi-module-group-verification.json) | **场景已验**：创建、排序、发布后的顶栏组和左侧模块切换在隔离系统 #166 验过；最终旅程还需回归。 |
| [5.4 模块](../../../../docs/user_requirement.md#54-模块) | [模块配置接口](../../backend/src/main/java/com/unique/unexamine/moduleconfig/manage/ModuleConfigurationController.java)，[生命周期](../evidence/s18-module-lifecycle-verification.json)、[打印升级](../evidence/s18-module-print-upgrade-verification.json)、[审批动作](../evidence/s18-module-approval-action-live.json)、[仪表盘入口](../evidence/s18-module-dashboard-action-live.json) | **局部**：打印、审批和仪表盘入口各有场景证据；旧版已发布模块首页的升级兼容和新旧模块全部动作组合未封板。历史 PDF 文件缺失仍须在完整数据副本复验。 |
| [5.5 字段](../../../../docs/user_requirement.md#55-字段) | [字段设计组件](../../frontend/src/product/components/ModuleFieldProperties.vue)、[字段场景](../evidence/s18-module-fields-verification.json) | **局部**：普通、关联、子表字段在模块 #92 验过；全部字段类型、规则、敏感策略及组合变更尚未逐项封板。 |
| [5.6 字典](../../../../docs/user_requirement.md#56-字典) | [字典配置页](../../frontend/src/product/views/DictionaryConfigurationView.vue)、[字典接口](../../backend/src/main/java/com/unique/unexamine/moduleconfig/manage/DictionaryConfigurationController.java) | **待核**：有入口和实现；层级字典、级联来源、版本与引用影响尚未找到完整当前业务验收。 |
| [5.7 部门和成员](../../../../docs/user_requirement.md#57-部门和成员) | [成员邀请页](../../frontend/src/product/views/MemberInvitationView.vue)、[人员目录接口](../../backend/src/main/java/com/unique/unexamine/authorization/manage/SystemPeopleDirectoryController.java)，[部门范围](../evidence/s18-app-department-scope-verification.json) | **局部**：邀请、部门范围与失效拒绝有场景证据；部门树、选择器及被引用对象变更的完整处理需横向验收。 |
| [5.8 页面](../../../../docs/user_requirement.md#58-页面) | [列表、表单、详情设计组件](../../frontend/src/product/components/ModulePageDesigner.vue)，[自动页面](../evidence/s18-module-overview-verification.json) | **局部**：默认页面生成已验；全部页面类型、布局、个人列宽/固定列、移动展示及发布回滚未形成整项验收。 |
| [5.9 菜单](../../../../docs/user_requirement.md#59-菜单) | [系统运行页](../../frontend/src/product/views/RuntimeWorkspaceView.vue)，[多组导航](../evidence/s18-multi-module-group-verification.json)、[权限预览](../evidence/g10-permission-preview-verification.json) | **局部**：组与模块可见性有证据；所有菜单树操作、绑定、隐藏、排序和撤权仍需同版回归。 |
| [5.10 权限](../../../../docs/user_requirement.md#510-权限) | [授权页](../../frontend/src/product/views/SystemAuthorizationView.vue)、[授权接口](../../backend/src/main/java/com/unique/unexamine/authorization/manage/SystemAuthorizationController.java)，[G10](../evidence/g10-permission-preview-verification.json) | **局部**：代表角色、动作、字段、SELF/ALL 与撤权已验；多角色并集、跨租户共享并集及每个动作的数据范围尚未全量组合验收。 |
| [5.11 数据记录](../../../../docs/user_requirement.md#511-数据记录) | [运行数据接口](../../backend/src/main/java/com/unique/unexamine/runtimedata/manage/RuntimeDataController.java)，[G2](../evidence/g2-current-integration-verification.json)、[归档恢复](../evidence/g11-record-archive-recovery-verification.json) | **局部**：CRUD、转移、转化、关联、归档有多条场景证据；不同字段和动作组合、已有模块回归、共享记录写操作需封板。 |
| [5.12 Flow](../../../../docs/user_requirement.md#512-流程) | [设计页](../../frontend/src/product/views/FlowDesignerView.vue)、[运行接口](../../backend/src/main/java/com/unique/unexamine/flow/manage/FlowRuntimeController.java)，[审批写入](../evidence/s02-flow-approval-write-verification.json)、[外部 Flow](../evidence/s04-external-flow-configuration-verification.json) | **局部**：设计、发布、审批及应用开放有场景证据；全节点类型、租户覆盖优先级、同审批人后续自动通过规则需逐项验收。 |
| [5.13 文件](../../../../docs/user_requirement.md#513-文件) | [文件页](../../frontend/src/product/views/FileWorkspaceView.vue)、[文件接口](../../backend/src/main/java/com/unique/unexamine/file/manage/FileController.java)，[新系统贯通](../evidence/s18-product-acceptance-inventory.json) | **局部**：附件上传和详情刷新已在一条旅程复核；预览、分片、引用删除和存储配置全链路未封板。 |
| [5.14 模块导入导出](../../../../docs/user_requirement.md#514-模块数据导入导出) | [导入组件](../../frontend/src/product/components/ModuleImportDrawer.vue)、[导出组件](../../frontend/src/product/components/ModuleExportDrawer.vue)，[G6](../evidence/g6-module-data-exchange-verification.json) | **场景已验**：Excel/CSV、预演、异步、重复与回滚有组合证据；最终需在现行模块动作权限与发布版本下回归。 |
| [5.15 应用访问](../../../../docs/user_requirement.md#515-应用访问能力) | [桥接接口](../../backend/src/main/java/com/unique/unexamine/application/manage/ApplicationBridgeController.java)，[跨系统](../evidence/s05-cross-system-application-verification.json)、[S18.17](../evidence/s18-system-application-verification.json) | **局部**：已发布资源和签名访问有实际场景；全部动作、字段、租户、失败日志需随最终业务版本复验。 |
| [5.16 消息](../../../../docs/user_requirement.md#516-消息) | [消息页](../../frontend/src/product/views/MessageWorkspaceView.vue)、[消息接口](../../backend/src/main/java/com/unique/unexamine/notification/manage/MessageController.java)，[G2](../evidence/g2-current-integration-verification.json) | **局部**：审批/任务消息有场景证据；各来源通知、未读、归档和权限跳转需横向验收。 |
| [5.17 日志审计](../../../../docs/user_requirement.md#517-日志和审计) | [审计页](../../frontend/src/product/views/AuditEventsView.vue)、[查询接口](../../backend/src/main/java/com/unique/unexamine/audit/manage/AuditQueryController.java)，[G13](../evidence/g13-operation-risk-prompts-verification.json) | **局部**：多条业务回执核对过审计；全部事件种类、不可变留存和按人/时间/系统/应用检索未封板。 |
| [5.18 系统后台](../../../../docs/user_requirement.md#518-系统后台配置细化) | [后台首页](../../frontend/src/product/views/SystemAdminHomeView.vue)、[设置页](../../frontend/src/product/views/SystemSettingsView.vue) | **局部**：一级能力有入口；须逐项检查 A.15 的类别边界，避免同一配置出现多个产品入口。 |
| [5.19 AI](../../../../docs/user_requirement.md#519-智能能力) | [平台 AI 配置](../../frontend/src/product/views/PlatformAiConfigurationView.vue)、[系统 AI 页](../../frontend/src/product/views/AiWorkspaceView.vue)，[G3](../evidence/g3-dashboard-application-agent-verification.json)、[采购 Agent](../evidence/s18-ai-procurement-acceptance.json) | **局部**：权限查询和确认写入有本地模型夹具证据；其他生成能力和按需启用的真实模型连接需按实际配置核对。 |
| [5.20 任务](../../../../docs/user_requirement.md#520-任务普通任务项目任务工作日志日历) | [任务页](../../frontend/src/product/views/WorkHubView.vue)、[项目页](../../frontend/src/product/views/WorkProjectsView.vue)、[日志页](../../frontend/src/product/views/WorkLogsView.vue)，[G14](../evidence/g14-daily-productivity-verification.json) | **局部**：日常任务/待办组合有证据；项目、普通任务、手动工作日志、看板/日历和统计的完整关系尚未整项验收。 |

## 最终澄清与上线规则的横向检查

| 需求边界 | 当前判断 | 收口重点 |
| --- | --- | --- |
| [A.14.1–A.14.4 身份、扩展、共享、角色并集](../../../../docs/user_requirement.md#附录-av2-已确认产品澄清2026-06-15) | **局部**：G1、G10 与租户扩展页证明了部分路径；尚无覆盖“多角色 × 多租户 × 共享动作 × 发布版本”的同一对象组合验收。 | 使用主租户与协作租户、两个有效角色和两条共享授权，逐动作核对列表、详情、写入、导出、撤权及会话刷新。 |
| A.14.5–A.14.6 动作、转化、模块发布、Flow 快照 | **局部**：G2、G12、S18.30–S18.36 覆盖打印升级、新模块审批、模块仪表盘入口、旧首页升级兼容，以及同一模块打印/审批/首页/导入/导出/转移/转化旅程；隔离库缺失的旧打印文件字节与其他 Flow 组合尚未封板。 | 继续复验其他 Flow 节点、条件和版本切换，并用完整文件备份复验旧打印作业。 |
| A.14.7–A.14.9 平台独立能力、应用安全、租户 Flow 优先级 | **局部**：平台 Flow、应用与安全策略有回执；租户覆盖与运行快照未见完整当前组合。 | 平台与系统/租户分别建 Flow，经授权应用跨域执行，核对版本及最终生效绑定。 |
| A.14.10–A.14.13 租户扩展、共享行为、转化唯一性、模式切换 | **局部**：扩展 UI、共享后端和 G2 转化存在；最新规则未形成一条完整租户切换及共享旅程。 | 验主租户发布和扩展冲突、独立仪表盘、共享数据列表来源、撤销及关闭/重开多租户。 |
| A.14.14 删除、归档及永久审计 | **局部**：[G11](../evidence/g11-record-archive-recovery-verification.json)覆盖回收和恢复；保留策略下的物理清理及审计留存仍待核。 | 删除被引用数据、恢复冲突及清理作业后核对永久审计。 |
| [A.15 信息架构](../../../../docs/user_requirement.md#A15-主页面后台与应用边界统一2026-08-15冲突时最终优先) | **局部**：当前路由和后台入口存在；模块动作、页面、菜单、模板分类要按最新口径整体走查。 | 管理员与普通用户从登录页逐层走导航，排除重复入口和技术表单。 |
| [A.16 通用数据与移动应用](../../../../docs/user_requirement.md#A16-低代码数据移动端与应用桥接最终澄清2026-08-17冲突时最终优先) | **局部**：通用 `un_biz_*` 记录结构与应用桥接有证据；[手机窄视口采集](../evidence/s14-mobile-capture-verification.json)不等于完整移动端适配。 | 固定桌面和移动断点，各完成普通用户完整业务旅程。 |
| [A.17 通用骨架和审批规则](../../../../docs/user_requirement.md#A17-通用业务骨架与本轮交付澄清2026-09-05) | **局部**：共享页面与动作处理存在；同审批人后续节点自动通过规则未见专项验收。可选渠道未配置时不列阻塞。 | 核对默认 CRUD 与已配置 Flow 的切换、审批解释和审计。 |
| [A.18 引用和生成顺序](../../../../docs/user_requirement.md#A18-表结构业务引用与生成顺序最终澄清2026-10-08) | **局部**：首装表与生成 Base 已核对，[G13](../evidence/g13-operation-risk-prompts-verification.json)覆盖部分影响提示。 | 对被引用对象的删除、停用、转移逐类检查可执行处理和事务审计。 |
| [A.19 表名、SQL、数据源和 Flow 对外](../../../../docs/user_requirement.md#A19-统一命名模块-sql-与数据源边界2026-10-09冲突时优先) | **局部**：当前 190 张 `un_` 表及 [数据库三份输出](../../db/README.md)符合主线；[数据源](../evidence/s18-dashboard-datasource-acceptance.json)、[外部 Flow](../evidence/s04-external-flow-configuration-verification.json)有场景结果。 | 同版复验预览、运行、下钻、导出和 AI 的权限边界及跨系统应用桥接。 |
| [14.1–14.13 发布、数据与风险](../../../../docs/user_requirement.md#14-上线能力完善规则) | **局部**：G6–G13 分别有场景回执。 | 在新旧模块、主租户与扩展租户上验配置影响、失败、回滚和历史快照。 |
| 14.14–14.20 体验、扩展、编码、升级、备份、安全 | **局部**：G14–G17 有场景回执。 | 对当前版本复验桌面/移动旅程、备份恢复与安全硬限制。 |
| 14.21–14.28 文档、API、错误、幂等、限流、缓存、前端守卫 | **局部**：代码有 API 与安全控制，[部署 README](../../deploy/README.md)和管理员、成员、应用接入、打印设置、排错等[独立手册](../../deploy/guides/README.md)已存在；其内容与当前候选的实际页面、脚本是否一致仍待核对。 | 在同一新装候选中按手册完成业务和运维操作，记录错误或缺项，不把文件存在等同于手册验收。 |
| 14.29–14.37 统计、审计、灰度、配额、脱敏、移动与数据设计 | **局部**：看板、审计、运维入口存在；按 [A.13](../../../../docs/user_requirement.md#A13-工程重整与界面边界2026-08-10冲突时优先)，容量和大并发不属于核心框架默认压测，完整移动适配属于功能后的 hardening。 | 完成功能封板后核对移动断点、可访问性、配置版本、审计与发布制品。 |

## 收口顺序与完成判据

1. **P0 处理已证实回归**：S18.29 提供不破坏旧版本和任务的 PRINT 过渡；V22 把升级时已有旧模板固定到兼容清单，避免要求所有管理员同步操作。隔离库 V21→V22 前后模块版本、模板指针、业务记录和旧作业摘要一致，旧模块 #67 的页面、API、新 PDF、权限及过渡后退出兼容已实测，新装库集成测试验证新模板仍受动作发布约束。历史任务字节在独立 MySQL+文件存储集成测试中验证不变；隔离库旧文件缺失仅限制该旧夹具的证明力。当前候选只使用完整的新建单据和模板数据复验历史模板打印。
2. **P0 锁定发布基线**：审批和仪表盘入口已接入模块发布动作；V23 对升级前已发布首页建立限定清单。模块 #94 的打印、审批、首页、导入、导出、转移、转化和条件/并行 Flow 有同版局部证据。当前先清除构建和测试红灯，并以第 2.3 节和第 10 章锁定整站旅程；S18.39 的包容网关继续记为未验，不先于产品级发布关口实施。
3. **P1 完成产品级业务旅程**：从空库新装开始，用管理员、普通成员、审批人、无权限用户，在两个系统及同系统多租户中完成建模、发布、录入、审批、附件、打印、导入导出、仪表盘、AI 确认、消息待办、应用跨系统访问；每个关键动作检查页面、API、数据读回、拒绝、撤权和重启。主租户与协作租户的扩展、共享、独立仪表盘另作一条旅程。
4. **P1 全域需求追溯**：把本表每个“局部/待核/缺实现”拆成可执行用例；对 5.1–5.20、14.1–14.37、A.14–A.19 的每条必需行为给出当前结论、入口、正反结果和证据。先完成核心业务和当前启用配置的签收；JIT、AD 特定行为和企业渠道记录为按配置验收的未闭合能力，不以未提供真实 IdP 凭证或旧测试文件字节阻断核心交付。缺实现修实现，缺证据补验收，旧口径回执标失效，不能仅改台账状态。
5. **P2 最终交付**：形成管理员、用户、应用接入、打印、配置和排错说明；生成有版本与校验值的部署包，在独立环境完成首装、升级、备份恢复、桌面及移动旅程。只有全量功能与交付物都签收，才把 `productCompletion` 改为已验证。

下一项实施按产品级发布关口的当前任务包执行 `execution-context`，用真实业务结果和 `execution-verify` 留证；本对账不修改门禁哈希或历史回执，也不授予整体完成状态。
