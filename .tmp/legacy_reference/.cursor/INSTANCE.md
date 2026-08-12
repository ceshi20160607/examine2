# examine2 工程实例

## 1. 实例身份

- 实例：`examine2-engineering`
- 项目：`examine2`
- Base 版本：`3.2.0`
- 项目目标：依据唯一需求源构建一个普通管理员能配置、普通成员能使用、外部系统能安全接入且可部署维护的业务管理系统平台。

`.base` 是可复制、可版本化的通用多 Agent 工程框架；`.cursor` 是本项目从 Base 复制后形成的实例。项目发现的通用缺陷应先形成失败用例，再回灌 `.base`，由后续项目复用；项目业务事实不得反向污染 Base。

## 2. 唯一权威需求源

| 用途 | 路径 | 权威性 |
|---|---|---|
| 产品需求 | `docs/user_requirement.md` | 唯一业务事实源 |
| 本地配置 | `docs/user_setting.md` | 仅在具体运行任务需要时最小读取，不是产品需求 |
| 其他 docs、旧原型、旧计划、旧 evidence | 各自原路径或 `.cursor/session/archive/` | 只作参考，不得自动晋升为需求或完成证据 |

需求源 SHA-256 由 `.cursor/session/project-delivery.json` 固定并由校验器读取真实文件复算。聊天摘要、历史验收文件和 Agent 共识不能替代源文件。

## 3. Base v3 执行合同

- 项目编译：`.cursor/skills/compile-project-delivery/`
- 管理页面规范：`.cursor/skills/build-management-ui/`
- 后端模块实现：`.cursor/skills/implement-backend-module/`
- 分层测试策略：`.cursor/skills/verify-by-delivery-level/`
- 唯一交付合同：`.cursor/session/project-delivery.json`
- 唯一运行状态：`.cursor/session/state.json`
- 粗粒度项目进度：`.cursor/session/project-progress.json`

未来阶段只保留 `outline`；当前阶段才允许变成 `detailed`，产生原子需求、验收用例、小任务和四小时周期。实现任务不得补充新产品语义。

## 4. 任务与多 Agent

- 每个任务只对应明确需求、验收用例、允许修改范围、禁止修改范围和 Base 复用方式。
- 单任务估时 `10..120` 分钟；每个周期最多 `240` 分钟关键路径并只演示一个用户结果。
- 没有依赖且文件、数据库、端口和测试资源不冲突的任务默认并行；多 Agent 仅用于这些独立写集。
- 有共享 migration、聚合事务、公共合同或相同资源的任务必须按依赖串行或隔离。
- 旧版角色 `update.md` 已归档，不能再覆盖 Base v3 的任务与周期上限。

## 5. 测试层级

- task：只跑受影响单测和受影响编译。
- cycle：周期结束只跑一次组合集成和一次真实入口。
- module：模块完成后按页面旅程、API/DB 读回和权限正反例验证。
- phase：验证该阶段承诺的完整可用结果。
- project：所有功能完成后只跑一次全量功能验收。
- performance：不属于核心工程流程；只有项目功能全部完成且项目所有者另行明确授权时，才可建立独立可选计划。

测试 PASS、业务结果达成和需求所有者验收是三个不同状态。错误码、403、构建成功、截图或文件数量不能冒充业务成功。

## 6. UI 产品边界

管理页面开发前必须生成并通过 UI 合同。模块标题与紧凑主操作同一行；模糊搜索与高级筛选分工明确；批量动作只有勾选后出现；行点击打开右侧详情；行内不重复“查看详情”；详情使用右侧抽屉和标签页；仪表盘以可视化拖拽为主。没有明确需求和安置决定时，不默认增加收藏、关注、运营、报表或 JSON 编辑入口。

## 7. 当前重构边界

现有代码和历史 evidence 不再作为完成事实。与新合同一致且独立可验证的基础设施可以复用；无关旧产品面冻结，不继续横向增加功能。第一期先完成工程边界、数据库启动、登录、注册创建首个系统、我的系统和系统壳，再滚动展开后续阶段。

## 8. 启动顺序

1. `.cursor/INSTANCE.md`
2. `.cursor/session/state.json`
3. `.cursor/session/project-delivery.json`
4. `.cursor/LEADER.md`
5. `.cursor/GOVERNANCE.md`
6. 当前任务需要的 Skill 和冻结 artifact

没有列入当前任务输入的旧计划、旧聊天、旧 evidence 和旧页面实现不主动读取。
