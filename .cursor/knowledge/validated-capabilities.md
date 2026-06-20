# 已验证能力清单（.oldbk 参考）

> **含义：** 后端域能力在 v1 已打通接口链路（trial deployable），  
> **不意味** UI 可用或可复制 manage 实现。重建时按新 API/UI 写 manage，base 用生成器。

## 后端模块与能力域

| 模块 | 能力域 | 参考路径 |
|------|--------|----------|
| examine-plat | 认证会话、平台账号/角色、系统创建、平台配置 | `.oldbk/backend/examine-plat/manage/` |
| examine-module | 系统上下文、成员/RBAC、字典、应用/模块/字段/页面、发布、运行台记录、**动作权限（含导出）** | `.oldbk/backend/examine-module/manage/` |
| examine-flow | 流程模板、实例、待办、审批动作 | `.oldbk/backend/examine-flow/manage/` |
| examine-upload | 上传、预览、下载、引用计数 | `.oldbk/backend/examine-upload/manage/` |
| examine-app | OpenAPI 客户端、AK/SK、scope、限流、调用日志 | `.oldbk/backend/examine-app/manage/` |
| examine-core | 统一返回、异常、RequestContext、权限服务、审计 | `.oldbk/backend/examine-core/` |
| examine-generator | 按表前缀生成各模块 base（**v1 已存在，直接参考**） | `.oldbk/backend/examine-generator/` |
| examine-web | 启动、过滤器、全局配置 | `.oldbk/backend/examine-web/` |

## 已验证的技术点（可复用策略）

- Maven 父子 POM + Java 21 + Spring Boot 3.3
- MyBatis-Plus 生成 base；业务在 manage
- 表前缀分模块（见 backend-structure.md）
- 权限快照、字段权限、数据范围拦截
- OpenAPI 签名、nonce、幂等
- 流程与运行记录状态联动
- **examine-generator**：`scripts/generate-base-crud.ps1`、CLI 参数见 README

## 种子账号（用户决策）

- `platform_admin_root` / `123123aa` → 新 `sql/init.sql` 必须包含，作为平台内置超级管理员种子账号

## 刻意不继承

- 旧前端页面结构与导航
- 旧 PM「accepted」结论
- 旧 `.codex/state.json` 任务状态
- 旧文档中的 fullProjectDeployable=true

## 重建顺序（Gate 后）

1. 父 POM + 空模块骨架（可从 .oldbk 抄结构，不抄 manage 业务）
2. dba：`sql/init.sql`
3. generator：全模块 base
4. manage：按 `docs/api/api.md` + 原型逐域实现
5. task-accept 逐任务；最后 e2e 车系统
