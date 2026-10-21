# 后端分层和代码生成规约

参考提交 `70a3979a` 的 Controller、Service 接口、ServiceImpl 和 `examine-core/src/main/resources/simple` 模板。参考的是调用方向与生成职责；旧 CRM 类型、日志依赖、接口路径和业务逻辑不进入当前产品。旧模板的更新方法把新增标志传成 `false`，不能原样复用。

## 目录和所有权

- `back/` 是 Maven 后端工程，`back/pom.xml` 聚合 `examine-*` 模块。仓库根目录的 `docs/` 放需求和验收，`template_base/` 放生成器，`font_web/` 是后端验收后开发的前端根目录。
- 每个业务模块在 `com.unique.unexamine.<业务包>` 下平级放置 `base/` 和 `manage/`。表归属由 `back/base-codegen.json` 定义，不能把不同业务表集中到一个公共持久化模块。
- `base/entity`、`base/mapper`、`base/service/I*Service`、`base/service/impl/*ServiceImpl` 由生成器产出。`IService + ServiceImpl` 是每张表的基础 CRUD，不是需要业务人员重复手写的额外层。
- `manage` 只承载该业务域的特殊规则、授权、跨表读写、事务编排和对外 HTTP 入口。确有复杂业务时建一个同域 Service；简单表操作由 Controller 调用已生成的 Service，不能在 Controller 写查询条件、持久化或业务判断。

## 调用方向

`Controller（路由、参数绑定、Bean Validation、响应） → manage Service（业务规则、权限、事务编排） → base IService/ServiceImpl（本表基础 CRUD） → Mapper`。

基础操作无需特殊规则时，可由 Controller 直接调用 `base IService`，但 Controller 仍不能直接依赖 Mapper。Controller 不能调用 `page/save/updateById/getById` 后再自行拼查询、做权限或映射业务结果；这类逻辑须进入 manage Service。ServiceImpl 不能通过空类或给 Mapper 改名来冒充分层。跨模块调用先使用被调用模块已有 Service，仅在真实需要时新增最小接口。

旧 `simple/controller.java.ftl` 生成路由并调用 Service，`service.java.ftl` 声明业务 CRUD，`serviceImpl.java.ftl` 执行分页、保存、详情校验和删除。当前生成器沿用该职责：`templates/service.java.ftl` 和 `serviceImpl.java.ftl` 生成有方法体的基础 CRUD。当前接口都有租户和角色权限，不能生成无授权的通用 HTTP CRUD 路由；手写 Controller 只保留薄的 HTTP 适配代码。

## 改动检查

1. 先看需求、表归属和生成模板，再改表或业务代码；不编辑生成代码来放业务规则。
2. 新增/修改接口时检查 Controller 是否只调用 Service，Service 是否先检查租户/权限再执行持久化，失败结果是否明确。
3. 重新生成后检查 `base` 与模板一致，保证再次生成不会冲掉手写业务逻辑。
4. 编译相关 Maven 模块，真实数据库可用时再验证 HTTP、持久化、权限与失败结果；编译成功不代表业务验收完成。

当前打印模块提供可读样例：`PrintController` 只调用 `PrintService`，`PrintService` 处理租户授权、模块归属和预览规则，并调用生成的 `IUnPrintService` 完成基础 CRUD。模块团队入口也已改为 `ModuleTeamController` → `ModuleTeamService`。其余 Controller 仍需按业务域逐批迁移，不能因为两个入口通过编译就宣称全后端分层完成。
