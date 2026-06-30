# v1 失败教训（禁止重犯）

> 从 P7–P15 多次 accepted → retracted 与用户部署反馈提炼。
> **不是**要再走一遍才发现；Worker 读到此文件即视为已知。

## 1. 治理失败

| 现象 | 根因 | 现规则 |
|------|------|--------|
| 阶段 accepted 后又撤回 | PM 把功能存在当用户会用 | Gate + e2e + 你试用 |
| progress 与 review 矛盾 | 无硬闸门 | 只信 state.json + evidence |
| agent not found 仍算完成 | Orchestrator 本地冒充 | 事件记录 spawn_failed，任务不算 done |
| 200+ 文档当状态 | 文档膨胀 | knowledge 沉淀 + 分阶段目录 |

## 2. 产品/UI 失败

| 现象 | 根因 | 现规则 |
|------|------|--------|
| 普通人不知道怎么用 | 按 API 模块堆页面 | 按用户任务组织 UI；Open Design 你先签字 |
| che 登录看到平台后台 | 入口路由按管理员设计 | 单系统普通用户直达运行台 |
| 先建平台账号再加系统 | 暴露底层账号模型 | 统一认证：平台用户表唯一；注册=建系统+超管；系统内只扩展成员 |
| 页面像调试台 | 裸露技术 ID/接口名 | ui-spec 业务文案；frozen-rules |
| 配置态与使用态混在一起 | 同一导航 | 分入口 + 权限隐藏 |

## 3. 技术/验收失败

| 现象 | 根因 | 现规则 |
|------|------|--------|
| 后端通过=全项目完成 | 验收维度单一 | 拆开 backend/frontend/deployable/e2e |
| 只有 tsc/build | 无真实 UI | 必须 dist + 浏览器剧本 |
| 管理员链路 alone | 未验普通用户 | 车系统/che 为 P0 剧本 |
| 成员上下文靠前端带头 | 权限可伪造 | 服务端绑定 account+member |

## 4. 流程失败

| 现象 | 根因 | 现规则 |
|------|------|--------|
| 20 步 Pipeline 跑到底 | 瀑布式 Agent | Work Graph + 可停 Design Gate |
| 聊天上下文传递 | 幻觉、假完成 | 新会话只读文件 |
| 实现者自验 | 既当裁判又当选手 | task-accept / test 分离 |
| 无 UI 冻结就写前端 | 跳过设计 | design_user_approved 硬停 |

## 5. 不必重跑的「错误过程」

以下 v1 环节 **跳过**：

- 多轮「理解评审」争论平台/系统是否分层 → **已冻结** domain-model.md
- 争论 base/manage 是否分离 → **已冻结** backend-structure.md
- 争论是否要先 UI 再开发 → **已冻结** gates.md
- 用 PM 口头「继续」推进无验收批次 → **禁止**

Phase 0 只做：**需求 vs knowledge 差异扫描**，不是全员重新理解一遍。

## 6. 仍须验证的（不能假设 pass）

- 新 UI 原型是否真能让你满意 → **你签字**
- 新 API 是否与原型字段一致 → contract 阶段多角色共笔
- 新代码是否跑通车系统剧本 → e2e + 你试用

旧 `.oldbk` 代码**不能**作为「已验收」证据，只能作为能力参考。

## 7. 设计原型复审失败（2026-06-18）

| 现象 | 根因 | 现规则 |
|------|------|--------|
| 旧导航关键词清掉了，但页面仍不好用 | 只做静态路由/禁用词检查，没有做布局合理性复审 | 原型通过前必须做 UI/UX 视觉复审，检查比例、控件形态、业务数据和真实交互 |
| 组织架构左侧占宽不合理且不是树 | 用通用 split + stepper 代替部门树 | 组织架构必须左树右表：左树 240-280px、父子层级、缩进、展开/收起、节点操作；右侧员工列表占主宽度 |
| 流程管理像节点卡片堆叠 | 把“有节点”当成“有流程画布” | 流程设计器必须有左节点库、中画布、右属性面板、连线、箭头、条件分支标签和节点属性联动 |
| 运行态列表下方常驻导入导出面板 | 把操作反馈当页面内容铺在列表下面 | 导入导出只能从工具栏触发弹窗/抽屉/下拉；任务进度通过 toast、消息或任务结果弹窗反馈 |
| 详情 tab 内容混乱 | 复用权限矩阵/通用表格糊业务内容 | 详情 tab 必须是专属业务数据：基础资料、团队成员、费用/预算、附件、打印记录、操作记录各自独立结构 |
| 模块入口指向筛选/列设置状态页 | 为凑页面数量复用状态页 | 左侧模块导航必须指向独立模块列表或明确占位页，状态页不能伪装成模块 |
| 列设置、模块配置、审批面板复用通用矩阵/stepper | 把控件存在当设计完成 | 列设置、七步配置、审批面板必须是专属控件和业务结构，禁止复用权限矩阵和 stepper |
| 平台后台组织架构混入系统业务角色 | 平台层/系统层边界不干净 | 平台后台只展示平台账号、平台角色和平台组织；系统业务角色只出现在系统后台 |
| 聊天上下文越长越容易跑偏 | 多轮口头修正没有完全沉淀 | 下一轮 Open Design 优先使用无聊天上下文污染的新会话，只读更新后的 brief 和 knowledge |
| 用户指出一个问题才修一个问题 | 没有从“完整系统给正常人长期使用”这个最终目标倒推设计验收 | 原型审查必须先按完整系统闭环自检：平台层、系统层、普通用户、管理员、配置、运行、审批、权限、日志、导入导出都要能形成可开发设计 |
| 把“最终目标”理解成页面级完整 | 没有上升到整个项目的架构层级，导致只在页面壳里修补 | 设计前必须先冻结项目架构层级：平台层、系统层、运行态业务层、配置态管理层、权限身份层、流程审批层、数据集成层。页面设计只是这些层级的表达，不能倒过来用页面拼架构 |
| 登录入口、普通用户视角、系统切换被漏掉 | 只验“页面能点”，没有按真实用户从登录到业务首页到模块列表的路径复审 | 原型给用户看前必须走 P0 用户剧本：登录入口存在；普通用户只见权限内模块 + 待办 + 消息 + 个人信息；多系统账号有系统切换；系统后台必须与业务页分入口 |
| 运行态列表出现重复导入/导出和页内重复模块导航 | 未做同屏动作去重和信息架构复审 | 列表页同一动作只能保留一个主入口；导入/导出在工具栏触发弹窗/抽屉；模块导航只在所属导航区出现，不能在内容区重复占位 |
| 平台普通成员和平台管理员边界被漏掉 | 只按系统用户/系统管理员/平台管理员三角色推导，少了平台普通成员日常工作台 | 设计必须覆盖四角色：系统普通成员、系统管理员、平台普通成员、平台管理员；平台普通成员无平台后台入口，平台管理员才有平台后台管理 |
| 租户、字段、字典、Flow 只写概念没有配置深度 | 把“可配置平台”理解成有几个配置页面，未按无代码和企业工作流能力展开 | 系统后台必须表达单/多租户、字段类型库、字段专属配置、字典版本和引用影响、企业级 Flow 节点库/属性/模拟/发布检查 |
| 新 AI Agent 需求容易被当成普通功能按钮 | 没有先沉淀平台级模型授权、系统级 Agent 权限和审计边界 | AI Agent 必须先写入原始需求和 brief：平台配置本地/外部模型和授权购买，系统配置可访问模块/字段/动作/数据范围，对话写入必须人工确认并受权限、脱敏和日志审计控制 |
| 待办和消息被做成小卡片/抽屉 | 没按真实企业用户每天处理事项的工作台来设计 | 待办必须是左侧类型 + 右侧列表，类型含审批待办、提醒待办、今日需处理；消息必须是列表 + 详情，并提供系统、租户、模板等筛选 |
| 登录只展示演示入口 | 把原型角色切换当成真实登录能力，漏掉注册和找回密码 | 登录页必须展示账号密码、MFA、注册、找回密码、SSO/验证码登录；注册创建系统后创建人成为系统超级管理员 |
| 企业 SSO 只有登录按钮没有后台配置 | 把身份认证当成运行态入口，漏掉身份源生命周期、系统继承、成员映射和审计边界 | 企业 SSO 必须有平台级身份源配置和测试发布：协议、证书/JWKS、回调、域名白名单、属性映射、JIT、MFA、系统继承、无成员映射反馈和登录日志 traceId |
| 系统 SSO 只放系统信息页一行说明 | 只表达“继承身份源”，没有系统级配置页，开发会漏组织映射、员工绑定和 JIT 系统成员策略 | 系统后台必须有一级“统一认证”页，联动组织架构：外部部门映射系统部门树，外部账号绑定平台账号、系统员工和 systemMemberId，未匹配对象进入预检/草稿/人工确认 |
| 后台入口被写死成某个角色 | 没体现可配置平台的权限来源 | 除平台内置超级管理员 platform_admin_root 和系统创建人超级管理员外，平台/系统后台入口、菜单、按钮、字段和数据范围都必须来自角色权限配置 |
| 新增日报/任务被机械塞成两个菜单 | 没抽象真实管理场景，只按功能名补页面 | 日报和任务必须按“工作管理”统一设计：项目、任务组、任务、协作、日报、日历、统计和消息/待办联动，不能做两个半成品孤立页面 |
| 待办页仍显示业务模块侧栏 | 把待办当成某个模块下的列表状态，而不是通用工作台 | 系统待办是独立工作台：左侧只显示待办类型，右侧显示待办列表和筛选；进入待办/工作/仪表盘时必须隐藏当前业务分组的模块侧栏 |
| 消息被做成表格列表页 | 混淆“消息通知”和“业务列表”，没有按用户读消息的真实习惯设计 | 消息从顶部入口打开右侧抽屉，按手机消息流一条一条展示完整内容；关联模块、系统、任务、项目、日志或 Agent 结果时必须提供跳转动作 |
| 日报和任务被拆成两个孤立菜单 | 只按功能名建页面，没有抽象真实协作场景 | 合并为工作管理：项目、任务组、任务、协作人、标签、完成度、日报、日历、统计、消息、待办和 AI Agent 形成闭环 |
| AI Agent 只写需求没有页面入口 | 把智能能力当成后续概念，导致设计确认前没有开发边界 | AI Agent 当前设计必须同时有后台配置和运行态对话入口；对话写入、统计查询、任务提取、日报草稿都要受权限、脱敏、人工确认和审计控制 |
| 消息还需要点小按钮才跳转 | 把消息当成操作集合，没有按通知阅读习惯处理 | 消息流中整条消息必须可点击：审批消息进入对应数据审批详情，模块消息进入对应数据详情，任务/日报消息进入对应工作对象；按钮只做辅助动作 |
| 工作模块把项目任务表和看板堆在一起 | 没区分信息架构和视图形式，让用户以为是两套任务 | 工作模块按“仪表盘 / 任务 / 日报”拆分；任务列表、任务看板、任务日历是同一批任务的不同视图，不重复建功能 |
| 日报候选内容看不懂 | 命名像新功能，缺少用途解释 | 改称“待写入日报的工作记录”：它只是从完成任务、待办、消息、业务日志整理出的日报草稿素材，不是新任务列表 |
| 消息卡片已经整条可跳转却仍放小按钮 | 只想着“有入口”，没从消息阅读空间和主操作唯一性复审 | 消息流里关联对象由整条消息点击跳转；卡片内不堆进入详情、打开任务、查看日志等重复小按钮，辅助动作需极少且有明确不同目的 |
| 工作模块没有拆清项目任务和普通任务 | 只按“任务”做列表，没区分项目协作和个人轻量任务 | 任务页必须拆项目任务和普通任务；项目任务支持列表/看板/日历切换，看板列来自项目状态配置；普通任务可一行创建并后补项目、标签、业务对象 |
| 日报页只做团队看板 | 忽略普通员工每天真实路径：先看自己的最近日报，再写今天 | 日报页默认展示我的最近日报，支持手动填写和自动统计今日日报；团队日报看板只是管理视角，不替代个人主线 |
| 登录页只有演示入口没有注册/找回页面 | 把原型抽屉当成基础账号流程 | 登录、注册并创建系统、找回/重置密码必须是可进入、可返回、可走通的页面流程 |
| 列表只有“详情”按钮才可打开详情 | 没按业务系统通用交互处理，把主动作缩到行内按钮 | 所有列表数据行点击都能打开详情；行内按钮只放编辑、删除、转移、审批、导出等差异动作 |
| 工作管理仍保留一个笼统“任务”标签 | 没把对象拆分落实到信息架构 | 工作管理固定为“仪表盘 / 项目任务 / 普通任务 / 日报”四标签；项目任务和普通任务分开承载 |
| 仪表盘月历被压成窄栏 | 只想着“同屏显示”，没有考虑信息密度和阅读顺序 | 本项目工作仪表盘中项目/今日预警与月历上下布局，先看风险再看日历，避免月历被压成不可读窄卡 |
| 长上下文中途压缩后信息丢失 | 关键决策只在聊天里，没有先写入文件 | 每次开工先做落盘压缩：读 state、brief/review、user_requirement、agent-operating-rules、failure-lessons；新纠偏先写 knowledge/review/state |
| 通用规约混入本项目规则 | 没分清所有项目通用方法和当前项目产品约束 | 通用规约只写所有项目适用的方法；unexamine 专属规则放 project-operating-rules.md |
| 任务列表和看板并排显示 | 把两个视图当成两个区域，导致页面拥挤和认知重复 | 项目任务、普通任务都通过按钮在列表/看板之间互斥切换；列表分页，看板无分页并滚动加载 |
| 日报页展示团队看板和自动统计大块内容 | 没按用户当前要求收敛成个人日报列表 | 日报页只展示我的日报列表和筛选项；写日报、自动生成草稿作为操作入口 |
| 工作仪表盘强行并排预警和月历 | 为了同屏导致阅读不清晰 | 本项目工作仪表盘预警与月历采用上下布局，优先清晰明了 |
| 工作配置藏在模块管理行内 | 把工作管理当普通模块，没有体现它支撑项目任务、普通任务、日报和看板的配置中心地位 | 系统后台必须有一级“工作配置”页面，配置项目任务/普通任务/日报字段列表、字段属性、看板取数字段和发布检查 |
| 状态颜色各页各用各的 | 只关心页面能显示状态，没有做系统级视觉语义 | 全系统状态颜色必须有统一语义：灰=待处理/草稿，蓝=处理中/当前，橙=待确认/临期，绿=完成/通过，红=失败/拒绝/逾期/停用 |
| 后台日志只有一张混合表，或拆成两个左侧菜单 | 把安全审计和业务审计混到一起，或把后台导航拆散导致入口混乱 | 后台只保留一个“日志管理”入口，页面内区分登录日志和业务日志；登录日志看账号安全，业务日志看模块数据、审批、导入导出、OpenAPI、AI Agent 和追踪号 |
| 个人信息和顶部系统切换重复 | 同一能力放两个入口，浪费顶部右侧空间并增加认知噪音 | 多系统进入只保留顶部“系统切换”；个人信息只放资料、安全、退出、按权限展示后台入口 |
| 工作配置直接维护状态、标签和统计 | 混淆对象字段配置和选项字典配置，导致无代码模型不清晰 | 工作配置只配置项目任务/普通任务/日报字段列表、字段属性和看板取数字段；状态、标签、颜色、图标由数据字典维护 |
| 任务看板按“标签功能”硬写 | 没把看板抽象成字段驱动视图 | 看板列、泳道、分组只能选择已发布的下拉单选或多选字段；看板颜色和图标来自字段绑定的数据字典项 |
| 平台应用页直接进入系统业务数据 | 绕过系统切换和系统成员映射，平台身份与系统身份混淆 | 平台到系统必须经过系统切换；只有存在系统成员映射、系统角色和有效数据范围时才能进入 |
| 平台运行态 Agent 或平台消息直接打开系统业务模块 | 共用系统业务 Agent 抽屉或业务模块目标，导致平台身份绕过系统切换 | 平台 Agent、平台消息和平台任务只处理平台授权/日志/任务/健康/切换引导；系统业务数据必须先系统切换后由系统 Agent 或业务列表处理 |
| 平台 Agent 复用系统业务写入确认 | 运行入口拆开了，但确认页仍共用，API 阶段会把平台任务和业务写入混成一个泛确认接口 | 平台 Agent 必须使用平台任务确认，只生成平台任务、平台消息和平台日志；系统业务写入才进入字段差异、审批、失败补偿和业务日志确认 |
| 知识文件仍写“系统包含业务应用、模块数据带 appId” | 旧模型未沉淀修正，容易导致 API/表结构跑偏 | 模块组只做导航和发布关系；业务数据归属 systemId/tenantId/moduleId；对外应用只做 OpenAPI 来源和审计 |
| 树状区域看起来像树但不能选节点 | 视觉上像配置页，实际没有右侧筛选和节点状态语义 | 组织树、模块树、角色树、字典分类、待办类型都必须可选中并刷新右侧内容 |
| 流程画布在常见宽度下裁节点 | 只检查有节点和连线，没有做视口可用性审查 | 流程画布必须有最小宽度、横向滚动、缩放或折叠属性面板，不能裁掉关键节点 |
| 关键配置抽屉写成说明页 | 为了不断链用结构说明替代可提交表单和结果反馈 | P0 配置入口必须有表单、校验结果、失败项、traceId、保存/发布/重试入口 |
| 开发契约级入口仍用通用抽屉 | 把“入口不断链”误判成“设计已完成”，导致平台 Flow、有效权限、流程高级运行、注册初始化仍让开发猜 | 会影响接口和任务拆分的入口必须有专属结构：字段、状态、版本、权限、失败反馈、traceId 和下一步操作，不能只复用 generic/default 详情 |
| 上线保障只写在需求/brief 里，原型看不到 | 把系统体检、备份恢复、功能开关、容量配额当成运维后补项，开发时会缺接口和页面边界 | 平台配置管理和系统信息页必须可见表达平台/系统体检、功能开关、灰度发布、容量配额、限流、备份恢复、归档恢复、最近备份、服务连通性和扩容申请 |
| 普通用户效率能力只停留在文字要求 | 没把长期日常使用的高频入口沉到运行态，后续会补全局搜索、草稿、快捷创建时返工 | 系统业务运行态必须提供全局搜索、最近访问、快捷创建、表单草稿和错误字段定位，并受角色权限、字段脱敏和数据范围控制 |
| 关键动作已有 drawerDetails 就当完成 | 把普通说明型抽屉和开发契约级模板混为一谈，导致审批、AI 写入、发布检查等仍不够开发落地 | 流程节点属性、审批处理、AI 写入确认、字典发布检查、AI Agent 发布检查和通用状态样例必须有专属模板；普通说明型抽屉才允许走 drawerDetails |

| 关键上线契约只在需求里，原型不露出 | 把多环境、部署回滚、API 规范、缓存策略当成纯技术实现，导致 API 阶段重新补页面和接口 | 平台配置管理和系统信息页必须有多环境/部署回滚/API 规范/缓存策略专属入口，签字前能看到边界 |
| 移动端扫码/拍照上传被当普通附件按钮 | 没把移动端采集、草稿、对象绑定、失败重试和审计纳入设计，开发时会返工附件模型 | 运行态快捷创建和附件抽屉必须表达收藏菜单、移动端扫码、拍照上传、附件草稿、权限脱敏、失败重试和业务日志 |

| 关键能力只用近义词暗示，开发拆分时仍可能漏字段 | 只检查“页面存在”，没有检查“需求词能否在原型中形成可开发证据” | 开发前覆盖锁定要做需求词到原型证据扫描；系统生命周期、消息筛选、字典类型、AI Agent 分层、容量配额、备份恢复、归档恢复、错误状态、禁用状态等必须显式出现在原型和 brief 中 |

| 只查“详情”按钮，没有查“查看 / 打开 / 进入”等同义详情动作 | 开发会继续做双入口，用户还会看到重复操作列 | 开发前操作列去重必须按语义扫描，整行点击是详情主动作后，操作列只留差异动作。 |

| 系统切换只是跳业务页，没有刷新成员上下文 | 把“能进入系统业务壳”误判成“已完成系统切换”，导致平台身份可能绕过 systemMember 权限 | 系统切换必须生成 SystemSwitchContext，明确 systemId、tenantId、systemMemberId、effectiveRoles、dataScope 和权限快照；平台身份无系统成员上下文时不能打开系统业务页 |
| 全局给所有表格行加可点击手势 | 把说明表、配置矩阵和状态表也变成“像可点但点了没反应”的假数据列表 | 只有明确数据列表或业务核心列表才能有整行点击样式和 rowDrawer；其他表格不显示 pointer，也不绑定详情行为 |
| 平台消息中心复用系统消息抽屉 | 共用消息模板导致平台消息可以直接打开系统业务详情，绕过系统切换 | 平台消息中心和系统消息中心必须拆开；平台消息涉及系统业务时只能跳系统切换或平台授权/任务对象，系统消息才在系统成员上下文内跳业务详情 |
| 流程节点都打开同一套审批属性 | 只做了画布和节点，没表达条件、字段更新、外部 API、超时等节点的不同接口契约 | 流程画布必须有条件标签，不同节点类型必须有专属属性承接和发布检查字段 |
| 消息筛选里有“模板”但没有模板配置对象 | 把消息当展示结果，没把通知模板、渠道、跳转目标、去重和失败重试沉成后台配置，开发会临时硬编码消息 | 消息、待办、审批、导入导出、Agent 结果必须有消息模板与通知渠道配置，包含 templateCode、变量、渠道、跳转目标、去重键、已读回执、失败重试和 message_delivery_log |
| SSO/外部应用页面写“密钥轮换”但没有 SecretRef/任务对象 | 开发容易把 secret 当普通字段保存和回显，后续安全审计返工 | SSO、OpenAPI、Webhook、外部模型密钥必须使用 SecretRef 和版本；轮换必须形成 SecretRotationJob，覆盖双写验证、切换、回滚和审计 |
| 未映射系统成员复用普通授权详情 | 认证成功但没有 systemMemberId 的状态被混进平台授权，可能误授权或绕过系统管理员审核 | 无系统成员映射必须生成 NoMemberAccessRequest；审核通过并分配角色/数据范围前不能进入业务页 |
| 关键按钮没有 data-toast 但仍走泛化“操作已响应” | 静态检查只看 data-toast=0，漏掉无承接主按钮，开发不知道接后台任务、发布检查还是日志 | 保存、发布、审批、AI 写入、模板发布、密钥轮换、体检、恢复演练等按钮必须显式接同步结果、后台任务、发布检查、日志追踪或专属抽屉 |

| 多轮复审仍靠同一聊天上下文继续看 | 上下文污染和疲劳导致只能补用户指出的问题，无法主动发现同类问题 | 反复出现明显缺口时必须拉干净上下文审阅者，只读落盘文件和当前原型，按产品/UX/权限/QA 合并发现后再改 |
| 系统切换只改标题或跳业务页 | 没把系统切换理解成系统成员、租户、角色、字段权限、数据范围和业务壳的整体重建 | 系统切换必须根据 accountMemberBindingId/SystemSwitchContext 刷新品牌、模块分组、左侧模块、列表、待办、消息和字段权限 |
| 多租户入口只有概念没有上下文对象 | 单/多租户配置写在系统信息里，但运行态不知道当前租户角色和数据范围 | 多租户系统必须有 TenantSwitchContext；单租户隐藏入口，切换租户刷新菜单、数据范围、待办、消息和字段权限 |
| 未映射成员申请只写“申请授权” | 缺少申请生命周期字段，开发会复用普通授权或绕过系统管理员审核 | NoMemberAccessRequest 必须包含 requestId/status/identityProvider/externalUserId/targetSystemId/tenantId/requestRole/approverId/approveResult/roleIds/dataScope/rejectReason/traceId |
| AI Agent 只写模块范围 | 没把字段、动作、数据范围、外发限制、脱敏和策略版本沉成契约 | 系统 Agent 必须保存 AgentPolicyScope，运行日志记录策略版本、模型授权版本、提示词版本和权限快照 |
| 外部应用还用 appKey/secret 语言 | 安全模型容易退回明文字段和直接覆盖 secret | 对外应用使用 OpenApiSecretRef、版本、轮换状态、到期和最近使用，不展示明文 appKey/secret |
| 消息筛选和分页按钮能点但没状态 | 只做断链检查，漏掉“按钮没有结果”的体验和开发契约问题 | 消息筛选、全部已读、归档、上一页、下一页、加载更多、筛选应用、列设置保存都必须有状态变化或结果承接 |
| 注册创建系统后缺少最终上线闭环 | 只引导到后台配置，没告诉管理员何时可以让普通成员使用 | 初始化引导必须包含发布业务首页、普通成员预览、发送登录入口和回滚方案 |
| defaultDrawer 被当作合理兜底 | 入口不断链被误判为设计完成，开发仍要猜字段、状态和错误处理 | defaultDrawer 只能表示设计缺口阻断态；P0 入口落到 default/generic 时不能 coding |

## 2026-06-25 release verification lessons

| Symptom | Root cause | Rule |
|------|------|------|
| Release script printed PASS after Maven failed on Java 8 | Native command exit codes were not checked in PowerShell | Release/package scripts must wrap native commands and throw on non-zero `$LASTEXITCODE`; a stale jar must never be packaged as a successful build |
| Frontend runtime API config loaded after built app script | Vite moved the module script into `<head>` while `config.js` stayed in `<body>` | Runtime config such as `/config.js` must be loaded in `<head>` before the built frontend module script, and release `index.html` ordering must be verified |
| Approval E2E had a workflow task but no visible todo/message | Runtime action created the approval task but did not close the user workbench notification loop | Any workflow action that creates a pending human task must create the matching todo and message in the same transaction or an explicit reliable outbox |
| Release frontend embedded a local backend API host for smoke testing | Local browser verification was treated as a deployable production shape | Production frontend packages must default to same-origin `/api/...`; Nginx owns backend host mapping, and package scripts must not inject backend hosts into frontend assets or `config.js` |
| Release defaults drifted from `docs/user_setting.md` | Test ports/local database defaults were copied into production `application.yml` and release templates | Release `application.yml` defaults must be reviewed against `docs/user_setting.md`: backend port `9999`, database `examine2`, Redis `192.168.0.211:6379/db10`; frontend origin must not appear in backend config for Nginx same-origin deployment |
| APIs trusted `X-Account-Id` from the browser | Local smoke compatibility was allowed to become the production identity mechanism | Production APIs must resolve current account from Redis-backed Bearer tokens; `X-Account-Id` may only exist behind an explicit local diagnostic switch defaulting to `false`, and frontend code must not send it |
| Login returned `登录会话存储不可用` while database/schema were UP | Release verification treated the process/API port as started even though Redis, the token session store, was DOWN | Release startup must run `/api/v1/health` and fail unless `status/database/schema/redis` are all `UP`; Redis connectivity is a hard deploy gate because login tokens depend on it |
| Frontend showed buttons that only opened success toasts | Prototype continuity was mistaken for finished coding, so users saw actions that looked usable but had no backend behavior | After prototype stage, unimplemented actions must be disabled, hidden, or wired to a real API/result state; never use success toasts to represent unfinished coding |
| Local release startup failed health but left the jar process running | The script threw after failed health without stopping the process it had just started | Startup scripts must stop the newly started process before throwing on health failure; a degraded service must not keep occupying the production port |
| Manual checks missed the same Redis/login failure twice | Verification was split across ad hoc commands and human interpretation | Release verification needs a single deterministic script that checks frontend config, Nginx proxy, Redis TCP, backend health, and admin login, and exits non-zero on any failure |
| Deployed page still showed old login buttons after the latest package was rebuilt | API health and login were checked, but the deployed `/index.html` was still pointing to an older frontend asset hash | Deployment verification must compare deployed frontend assets with the current release `frontend/index.html`; a backend-only PASS is not enough for user-visible acceptance |
| Real E2E created many test systems and polluted the platform workbench | Smoke scripts wrote into the shared `examine2` database and left created systems behind; Chinese test names were also corrupted by command-line encoding | E2E scripts must send JSON as UTF-8, use clearly recognizable ASCII test names, and clean up systems created by the current script by default unless a `KeepCreatedData`-style flag is explicit |
| Final R5 orchestration reported PASS while child output contained startup and verify failures | The wrapper trusted a missing/empty child PowerShell `ExitCode` and did not parse the child script's machine-readable `status` or fatal text | Final orchestration must fail if child stdout contains `"status":"FAIL"` or known fatal text such as backend health/startup failure, release verification failure, native command failure, Node script failure, or API failure; never accept a summary solely from a wrapper status |
| Release backend jar started with invalid zip/class loading errors after packaging | The release copy of `examine-web.jar` was not byte-identical to the Maven target jar, and packaging did not verify the copied artifact | Package scripts must hash-verify copied release artifacts, retry copy if hashes differ, and fail packaging if the release jar is not byte-identical to the build output |

## 2026-06-27 task definition recovery lesson

| Symptom | Root cause | Rule |
|------|------|------|
| Many files, pages, controllers, and smoke checks existed, but the running product still felt crowded, confused, and unusable | Tasks were defined as engineering outputs instead of small business outcomes; "page exists", "API 200", "generated CRUD", and "build passes" were allowed to masquerade as completion | Every coding task must be a role-based business task card with prototype reference, frontend scope, backend business API, generator boundary, data tables, permission rule, states, explicit non-completion cases, and an acceptance script |
| Prototype-driven work still degraded into copying a static shell | The prototype was treated as a visual reference instead of a functional contract with page/action/data/permission/script mapping | P0 prototype areas must be mapped in `docs/recovery/prototype-to-implementation-matrix.md`; a page cannot close until the corresponding role action works in the real system |
| Generator output was mistaken for finished functionality | Generated base CRUD was not separated from coded business behavior | Generated code is only plumbing. P0 behavior must live in coded manage/business services and be proven through a task card |
| Roles were split but the final product did not integrate | Role boundaries said who works where, but task cards did not force frontend, backend, database, permission, and state evidence to close together | Recovery coding may only use `docs/recovery/fix-batches.md`; each task closes only with full cross-layer evidence |
| Reviews and fixes risked turning into endless token-heavy loops | Missing tasks caused agents to rediscover scope through chat instead of reading a stable work plan | First write or update the recovery task card and matrix, then code. Do not start coding from vague feedback |
| Approval API returned APPROVED but the runtime record and todo still looked pending | The smoke script stopped at the workflow response and did not read back the business record detail, approval sidebar, action disabled state, or todo state after approval | Approval closure evidence must include workflow result, business record terminal status, approval sidebar terminal status, disabled repeat-submit action, pending todo count 0, handled todo readback, browser UI screenshots, and cleanup |
| Attachment fields appeared in requests but no real file was bound to the business record | Smoke data used fake attachment IDs and the runtime record service ignored `RecordSaveRequest.attachmentIds`, so upload, binding table, detail payload, and frontend download metadata were not one closed loop | Attachment acceptance must upload a real file, bind its returned `fileId` to `un_module_dynamic_attachment`, read back file metadata from record detail, preserve it across update, and keep frontend `downloadUrl` instead of proving only string persistence |
| Fresh system creation landed on an empty dashboard and looked like the product was still a pile of shells | Creation success was treated as the end of the flow instead of the beginning of system initialization | Fresh system creation must land on a role-based initialization checklist with direct next actions for organization, roles, modules, flows/dictionaries, members, and publish checks; empty dashboards must route admins to initialization rather than empty runtime pages |
| Approval tasks were created, but the submitter was also the approver | Workflow runtime ignored approval node assignee configuration and defaulted `assigneeMemberId` plus todo/message receiver to the current starter account | Approval acceptance must use separate requester and approver accounts, parse node MEMBER/ROLE assignee payloads, route todo/message to the assignee account binding, assert requester approval is denied, and only let the assigned approver complete the task |
| Direct `/platform/admin` or `/systems/{id}/admin` URLs showed login despite an active session | Hash-route SPA only read `location.hash`, so non-hash deploy URLs lost the intended route | Deployable frontend must normalize direct non-hash paths into hash routes before shell bootstrap; direct-route browser checks are required for admin and system URLs |
| Tenant switch smoke passed but non-empty business data could still show the old tenant | Context APIs stored the selected binding, but module/runtime/permission resolvers still picked the first account-member binding for the system; earlier smoke only checked empty lists and current-system metadata | Tenant acceptance must create non-empty data in at least two tenants, assert current member/context changes, assert each tenant shows only its own module and record in API and browser, and require business resolvers to use the active accountMemberBindingId |
| Work config update returned the submitted fields but a later GET and the browser still showed defaults | The update endpoint only wrapped the request into an in-memory response and never persisted to `un_module_work_config`; the smoke asserted the mutation response instead of readback | Admin configuration acceptance must PATCH, then GET the same object, assert persisted field code/name, and verify browser-visible data from the deployed frontend |
| R2 and R3 recovery smokes failed when run in parallel with the same admin account | Both scripts mutate the Redis-backed current `accountMemberBindingId`; parallel execution can switch one script into another script's system context | Context-switching E2E scripts that share an account must run serially, or use isolated accounts/tokens and assert the active binding before each context-sensitive write |
| Browser kept showing an older frontend behavior after release rebuild | The tab can retain an old JS asset until forced reload even when backend health and package verification pass | Browser evidence after a release rebuild must force reload and record the deployed asset hash before accepting UI-visible behavior |
| Standalone deployment still looked unchanged after a new package passed verification | `/index.html` or an already-open SPA tab can keep pointing at an old hashed JS bundle, so the user sees stale UI while backend health and API smokes are green | Deployment templates must send `Cache-Control: no-store` for HTML entry/fallback pages, verification must compare deployed asset hashes, and browser acceptance must force a full document reload and record the active JS asset |
| Final release orchestration can look stalled or lose the exact failure point | A long script kept too much state in memory and did not persist each child step result/log as it ran | Final acceptance scripts must write step results progressively, redirect child stdout/stderr to evidence logs, use per-step timeouts, and leave a machine-readable result file even on failure |
| An admin sidebar entry was left as an explicit pending placeholder after broader admin breadth passed | The batch accepted "API-backed or explicitly disabled" as an intermediate recovery state, then risked treating the explicit gap as final product readiness | A named P0 product gap cannot survive final-goal acceptance unless the user explicitly excludes it. If the prototype/brief requires the surface, add a recovery batch, implement persistence/API/frontend binding, and prove it through script plus deployed-browser evidence |
