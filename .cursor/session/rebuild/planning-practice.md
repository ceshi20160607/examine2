# 当前项目工程实践规划

更新时间：2026-07-10 Asia/Shanghai

## 三层边界

| 层 | 目录 | 职责 |
|---|---|---|
| 公共模板源 | `.base/` | 可独立复制的角色、技能、流程、Gate 和产出格式；只用于实例化和明确的模板维护 |
| 项目工程实例 | `.cursor/` | 当前项目自包含的角色、扩展、状态、节点、评审和证据；项目运行只读取这一层 |
| 项目输入 | `docs/` | 人可读需求、配置、流程和粗略设计，不承担工程调度与状态职责 |

`.cursor` 已经拥有公共角色契约的实例副本，运行时不读取 `.base`。公共角色位于 `.cursor/agents/{role}/role.md`，当前项目特殊规约位于同目录 `update.md`。

## 当前输入

| 文件 | 当前用途 |
|---|---|
| `docs/user_requirement.md` | 产品需求主来源 |
| `docs/user_setting.md` | 环境和运行配置，仅按最小范围读取敏感内容 |
| `docs/temp_flow.md` | 人可读功能与流程梳理 |
| `docs/temp_flow_persion.html` | 角色与流程交叉检查 |
| `docs/design/prototypes/index.html` | 粗略设计参考，不是冻结实现契约 |

## 工程阶段

### S0 公共模板与实例对齐

目标：公共模板可移植，当前实例自包含，项目输入边界清楚，旧代码不再作为来源。

核心产出：

- `.base/README.md`
- `.base/CONDUCTOR.md`
- `.base/agents/{role}/role.md`
- `.base/skills/README.md`
- `.base/workflows/lifecycle.md`
- `.base/templates/engineering-node.md`
- `.cursor/INSTANCE.md`
- `.cursor/README.md`
- `.cursor/CONDUCTOR.md`
- `.cursor/agents/{role}/role.md`
- `.cursor/agents/{role}/update.md`
- `.cursor/skills/README.md`
- `.cursor/workflows/lifecycle.md`
- `.cursor/templates/engineering-node.md`

状态：已完成。

### S1 需求理解

目标：从保留输入中形成一份人能看懂、后续工程节点能直接引用的完整需求理解。

唯一主产出：

- `.cursor/session/rebuild/requirement-understanding.md`

为控制文档数量，该文件集中包含：

- 需求总账。
- 角色旅程清单。
- 产品边界确认。
- 业务规则和术语。
- 来源冲突与开放问题。
- 进入 S2 所需的确认条件。

退出条件：每项后续设计或实现工作可以指向需求行和角色旅程；关键冲突已裁决或明确升级；编码 Gate 保持关闭。

### S2 设计完成

目标：把需求理解转成当前项目可执行的设计和工程契约。

主产出：流程与信息架构、交互状态、技术架构、数据库与领域方案、接口/权限/状态契约、生成与手写边界、测试设计。

退出条件：第一个编码节点不需要猜测关键需求；用户需要确认的设计已提供可阅读材料并得到确认。

### S3 编码

目标：按小型角色旅程切片实现。

顺序：数据库设计完成，生成基础持久化代码，手写业务行为，前端绑定真实接口。每个任务必须包含需求行、角色旅程、数据读回、权限、状态、副作用和证据。

退出条件：任务级独立验证通过，不能以生成 CRUD、接口成功、静态页面或构建成功单独认定完成。

### S4 调试与集成

目标：贯通前端、服务端、数据、权限、流程、消息、日志和浏览器行为。

主产出：问题总账、集成证据、失败状态覆盖、回归结果和更新后的执行状态。

退出条件：关键角色旅程从真实入口完成，失败可理解、可追踪、可恢复。

### S5 用户验收

目标：由用户对整个运行系统进行验收。

主产出：试用说明、整体验收清单、已知限制和用户签字记录或明确剩余阻断。

退出条件：只有用户实际试用或明确签字后，`gates.user_script_passed` 才能设置为 `true`。

## 当前下一节点

执行 S1 需求理解，先形成 `.cursor/session/rebuild/requirement-understanding.md`，不开始编码。
