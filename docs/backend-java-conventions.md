# 后端 Java 分层与写法约定（全模块）

与平台域已落地的写法对齐，**新增与重构代码**均按下列执行；历史代码可随需求逐步靠拢。

## 1. Spring 依赖注入

- **统一使用 `@Autowired` 字段注入**（参考 `AuthService`），**不使用**构造器注入声明依赖。
- 适用：`@Service`、`@RestController`、`@ControllerAdvice`、`@Aspect` `@Component`、以及注册为 Bean 的 `Filter` / `HandlerInterceptor` 等由容器管理的类。
- **例外**：纯 POJO、非 Spring 管理的 `new` 对象、以及单元测试中手动构造，不受此限。

## 2. MyBatis Mapper

- 每张业务表对应 **`com.unique.examine.{domain}.mapper.XxxMapper`**，SQL 放在同模块 **`mapper/xml/XxxMapper.xml`**（`namespace` 与接口全名一致）。
- **多表聚合 / 复杂查询**：优先写在**与主关联表或查询起点一致**的 Mapper 与 XML 中（例如账号-角色 RBAC 聚合写在 `PlatAccountRoleMapper`），**不单独**再建与表无关的 `*QueryMapper`，除非该查询在概念上完全独立且长期复用（再评估命名与归属）。

## 3. Service 与 Manage

- **模板 CRUD**：保持 **`I*Service` + `*ServiceImpl`**（MyBatis-Plus `ServiceImpl`），与代码生成一致。
- **手写编排**（跨多表、权限解析、业务流程）：放在 **`{domain}.manage`** 包下，**单个 `@Service` 类**即可，**不设** `impl` 子包。
- **Manage 只依赖同域 `I*Service`**，通过 Service 访问持久化；**不**在 manage 中直接注入 Mapper（避免与 §2 分层冲突）。

## 4. DTO

- 传输 / 树形视图等放在 **`{domain}.entity.dto`**，与 **`entity.po`** 表实体区分。

## 5. 模块边界

- **`examine-web`**：HTTP、鉴权会话、与前端契约；调用各 `examine-*` 模块的 Service / manage。
- **`examine-plat` / `examine-module` / `examine-flow` / `examine-app` 等**：领域服务与 Mapper；**不**在领域模块里写 Web 控制器（除非项目另有约定）。

---

与平台 RBAC 表相关的补充说明见 **[platform-permission-tables.md](platform-permission-tables.md)**。
