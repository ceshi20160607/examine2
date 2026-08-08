# 工程框架重建审计

更新时间：2026-07-10 Asia/Shanghai

## 结论

上一版角色文件没有被判定为“无用”。本次从上一提交恢复并审计了 conductor、pm、analyst、uiux、planner、dba、backend、frontend、test 的完整内容，同时审计了 failure-lessons、agent-operating-rules、context-policy、issue-protocol、work-graph、acceptance、final-goal-framework 和旧任务/旅程模板。

公共且经过失败验证的规则已经进入新版 `.base`；当前项目的需求、设计、任务计划和验收证据保留在 `.cursor` 实例。完成提炼后，重复且带过期动态状态的旧 architecture/knowledge/open-design/phase-* 文件已删除。

## 旧规则保留映射

| 旧框架有效内容 | 新位置 | 处理结果 |
|---|---|---|
| 我是谁、专业画像、我不是什么 | `.base/agents/{role}/role.md` 的第 1-3 节 | 完整恢复并标准化 |
| 必读文件、职责、工作模式 | 每个 role.md 第 4-6 节 | 改为可移植的输入类型和 mode |
| 角色使用技能 | 每个 role.md 第 7 节 + `.base/skills/README.md` + `.base/skills/playbooks.md` | 建立可校验引用和真实执行步骤 |
| 输出、Gate、会话、完成和禁止清单 | 每个 role.md 第 8-12 节 | 恢复并扩充输出规格 |
| 每次新会话、只读声明输入、不传聊天摘要 | `.base/GOVERNANCE.md` §2、`.base/agents/README.md` | 保留为硬规则 |
| PM issue 分流、三轮处理、无法解决找用户 | `.base/GOVERNANCE.md` §3-5、issue/meeting/pending 模板 | 改为 PM 会审 -> Leader -> 用户 |
| 任务 DAG、并行路径不重叠 | planner/pm 角色、lifecycle S3、task/node 模板 | 保留并明确 PM 拥有计划 |
| 实现者不能自验 | test 角色、GOVERNANCE §9、acceptance 模板 | 保留为硬 Gate |
| 页面/API/构建/生成 CRUD 不等于可用 | GOVERNANCE §8-9、lifecycle、task/acceptance 模板 | 保留为所有项目默认失败模式 |
| 最终目标 -> 角色旅程 -> 任务 -> 证据 | requirement/design/task/acceptance 模板 | 保留并作为主分解顺序 |
| 真实入口、权限正反例、状态、持久化读回 | product/uiux/backend/frontend/test 角色和模板 | 保留并扩充 |
| 原型不能代替功能，设计 Gate 前不编码 | uiux 角色、lifecycle S2/S3、当前角色 update | 保留 |
| 数据库先设计，generator base 与业务代码分离 | dba/backend 角色、design/task 模板 | 公共规则 + 当前 MyBatis 增量 |
| 交付不能只有裸制品，需健康/重启/回滚 | 新增 ops 角色、lifecycle S5、release-check | 从旧验收规则提升为独立角色职责 |
| 用户指出一处要扫描同类问题 | GOVERNANCE §8 | 保留 |
| 旧材料先盘点再删、文档不能膨胀 | GOVERNANCE §7、README §6 | 保留并用于本次审计 |

## 权责修正

| 旧问题 | 新模型 |
|---|---|
| conductor 只调度，没有真正的工程总负责人 | 改为 `leader`，负责总目标、质疑、节点验收和升级 |
| PM 同时兼产品经理和方案架构师，权力重叠 | 拆为 `pm` 项目控制、`product` 产品、`architect` 架构 |
| 后端/前端疑问只提交 PM，缺少固定会审参与人 | 固定由 PM 组织提交人 + product + uiux，按影响加入专业角色 |
| 发布/运维分散在验收规则中 | 新增 `ops` 独立角色 |
| 会议只有口头结论 | 新增 meeting-decision 模板，包含证据、分歧、决定、行动和复核 |
| 用户待决没有持续读取机制 | 新增 pending-user-decision 模板和 Leader 每轮检查规则 |

## 未进入公共模板的内容

以下内容不进入公共模板；仍属于当前项目事实的部分已经合并到 `requirement-understanding.md`、`design-package.md`、当前任务计划或验收证据：

- examine2/unexamine 产品术语和模块边界。
- 当前 docs 文件名和粗略原型。
- MyBatis 代码生成工具的项目选择。
- 当前环境路径、账号、数据库和 Redis 配置。
- 旧任务编号、旧 Gate 状态、历史 accepted/pass 记录。
- 当前项目特定 UI 规则和业务角色样例。
- 历史 architecture/knowledge/session 中仍有效的结论；原重复文件在提炼后删除。

旧文件不再作为备用事实源；当前主产出没有覆盖的旧结论视为未确认，不允许凭聊天或已删除路径恢复。

## 可执行性验证

- `.base/scripts/validate-framework.ps1` 校验公共模板结构、12 个角色章节、技能目录、25 个执行 playbook、UTF-8 和状态 JSON。
- 同一脚本使用 `-Instance` 校验当前 `.cursor` 的实例配置、角色和状态。
- `.base` 和 `.cursor` 的 `skills/playbooks.md` SHA-256 一致；实例中旧的项目特定技能文件已删除，正常执行不依赖 `.base`。
- S1 已形成并接受 57 条正式需求、52 条角色旅程和 8 条 NFR；S2 已形成并接受产品/UI/架构/数据/API/安全/测试/发布设计。
- VS1 已实际完成 schema-first、删除重生成、后端正反集成、空库重启和桌面/移动真实浏览器旅程，见 `.cursor/session/evidence/vs1/acceptance.md`。

## 角色能力判定

判定：当前框架足以组织并控制这类项目继续完成，但这个结论来自“角色契约 + 可执行 skill + 独立 Gate + VS1 实战”，不是来自文档数量。

| 能力层 | 机制 | 已验证 |
|---|---|---|
| 决策与总控 | Leader 唯一推进/退回节点；PM 组织最小完整会审；用户专属决定不代签 | S1/S2/VS1 三个 Gate 状态和会议记录一致 |
| 专业能力 | 12 个角色均有身份、边界、输入、mode、技能、输出、上报、完成和禁止清单 | 两个 framework validator 通过 |
| 执行能力 | 25 个 skill 规定必需输入、真实步骤、pass/fail 和 owner | validator 强制角色引用必须存在 playbook |
| 上下文隔离 | role + update + 声明输入；不传聊天摘要；主文件单 owner | `.cursor` 自包含，旧项目特定技能已移除 |
| 工程落地 | schema-first、generated base/manage 分离、真实 API、权限正反例、读回、浏览器和重启 | VS1 acceptance pass |

当前仍不能声明“每个角色已经完成全系统范围的能力证明”：动态模型、配置发布、Flow、文件、OpenAPI、AI、发布恢复等必须在 VS2-VS12 逐节点用同样证据验证。框架允许这些工作被正确组织和退回，但最终满意只能由完整系统和用户验收确认。
