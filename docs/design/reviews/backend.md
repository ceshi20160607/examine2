# Backend 设计包审阅（概念）

> agentId: backend | 非实现，仅边界

## 结论: pass（概念）

## 领域对象

| 对象 | 归属 | 关系 |
|------|------|------|
| ModuleGroup | 系统 | 1:N Module |
| Module | 系统 | 归属 ModuleGroup；字段/页面/菜单 |
| ExternalApp | 系统 | 对外凭证与授权；**不**包含 Module |
| PlatformUser | 平台 | SystemMember 扩展 |

## API 预埋（契约阶段再冻）

- 进入系统返回：成员权限 + 可见模块组 + 组内模块菜单树
- 运行态列表须带 moduleId + systemId + member 上下文

## 风险

无阻塞设计的概念冲突。
