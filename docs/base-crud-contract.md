# 基础 CRUD 模板合同

依据提交 `70a3979a` 的 `examine-core/src/main/resources/simple`。该提交的 Controller 把请求转给 Service；Service 声明 `queryPageList`、`addOrUpdate`、`queryById`、`information`、`deleteByIds`；ServiceImpl 执行查询、写入、详情和删除。旧模板的 CRM 类型、日志接口及更新参数错误均不复用。

当前模板位于 `template_base/mybatis-plus-generator/src/main/resources/templates/`，从已建表生成 `base/service/I*Service` 和 `base/service/impl/*ServiceImpl`。五个方法均有实际方法体：

| 方法 | 当前基础语义 | 业务边界 |
| --- | --- | --- |
| `queryPageList(PageQuery)` | 校验页码与大小，按表字段白名单做参数化等值筛选和排序，执行分页查询 | 租户 ID 等授权范围由 manage Service 确定后传入；复杂场景查询在业务 Service 实现，不能在 Controller 拼 SQL |
| `addOrUpdate(Entity)` | 主键为空新增；有主键先核对存在再更新；写入失败明确报错 | 授权、字段校验、并发版本和跨表事务由 manage Service 处理 |
| `queryById(Long)` | 读取本表记录；非法或不存在主键明确报错 | 资源归属与可读权限在 manage Service 校验 |
| `information(Long)` / 带布局参数的重载 | 基础表按字段备注返回默认半行宽度；业务传入字段宽度、顺序和扩展值时，按宽度累加计算行号及行内偏移。50% + 50% 同行；敏感字段不进入详情 | 模块业务 Service 从字段/表单配置和记录扩展值构造布局输入；`queryById` 仍只查询基础数据。默认布局不能替代配置布局 |
| `deleteByIds(List<Long>)` | 校验主键集并在 Service 事务中执行删除 | 业务删除、软删除和逐条权限检查在 manage Service 决定；不能把本方法直接暴露成不受保护的通用 HTTP 删除 |

通用代码生成器不发布没有租户/角色校验的 HTTP Controller。每个业务域在 `manage` 写薄 Controller：参数绑定、Bean Validation、`@Tag`、`@Operation` 和 Service 调用。生成代码不引用具体业务域的权限组件，也不写旧 CRM 类型。

验证要求：生成器自身编译不足以证明模板有效。`TemplateContractTest` 会渲染五方法模板并编译，对独立内存样表运行真实的 MyBatis-Plus 生成流程并编译实际输出；运行生成的 `information`，核对两个 50% 字段同行及随后 100% 字段换行；还将生成的 Mapper/Service 接入 H2，执行增删改查、等值筛选与真实 SQL 分页。业务表仍须从 `unexamine_r16` 的真实 MySQL 生成，再检查 HTTP、持久化、权限与错误结果。

运行生成器时可在配置文件路径后附本批表名，逗号分隔。只检查、生成本批表；不传时处理配置中的所有 94 张表。这样首批建表后即可生成，未建的后续表不会被当作已完成。
