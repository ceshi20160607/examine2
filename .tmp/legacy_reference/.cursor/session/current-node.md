# Current node

当前节点：`PHASE-P1-FOUNDATION-CLOSE`。

P1 已完成四个不超过四小时的交付周期：Foundation、登录、注册首系统、授权系统列表与系统上下文进入。阶段结论和四个周期的不可混用证据见 [P1 acceptance](evidence/baseline/PHASE-P1-FOUNDATION/acceptance.md)。运行态只暴露登录、注册、我的系统和最小系统配置引导，不暴露运营、独立报表、收藏、关注或未交付模块。

## 已形成的边界

- 生成 CRUD 只在 `base`；`manage` 编排产品动作并通过生成的 `IService` 使用持久化能力。
- P1 系统列表精确依赖 5 个 generated IService；系统切换精确依赖 11 个 generated IService。
- vNext manage/controller/domain 不直接使用 BaseMapper、JDBC、`java.sql` 或内联 SQL。
- 会话只在 Cookie 中返回原始 token，数据库仅保存 hash；current/refresh 从持久化授权快照恢复同形上下文。
- 前端 wire、store 和 switch 校验保持 strict required；旧页面没有生产兼容后门。

## 下一节点

下一节点为 `PHASE-P2-PLATFORM-SYSTEM-CONFIG-BASELINE`。开始 P2 开发前必须先从权威需求重新冻结平台、组织、系统管理、模块建模和可视化仪表盘的需求/任务/测试三联合同；不得直接复活旧路由、旧页面或历史 PASS 证据。

## 继续禁止

- 未经新周期合同直接修改 P1 已冻结功能和测试源码。
- 执行全量回归、性能、容量或百万/千万数据测试来替代当前阶段的功能验收。
- 在列表页恢复臃肿按钮、页面级收藏/关注、运营、独立报表或未交付占位入口。
