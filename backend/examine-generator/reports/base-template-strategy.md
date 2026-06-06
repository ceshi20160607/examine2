# GEN-003 base 层模板策略

## 结论

`examine-generator` 的 base 层模板策略已固定为只生成贴表基础能力：`entity`、`mapper`、`mapper.xml`、`service`、`serviceImpl`。生成器不生成 Controller、BO、VO、DTO、页面代码，也不在 base 层写业务校验、事务编排、权限过滤或数据范围逻辑。

## 模板清单

| 产物 | 模板 | 输出位置 |
| --- | --- | --- |
| entity | `src/main/resources/templates/base/entity.java.ftl` | `{module}/src/main/java/{basePackage}/entity` |
| mapper | `src/main/resources/templates/base/mapper.java.ftl` | `{module}/src/main/java/{basePackage}/mapper` |
| mapper.xml | `src/main/resources/templates/base/mapper.xml.ftl` | `{module}/src/main/resources/mapper/base` |
| service | `src/main/resources/templates/base/service.java.ftl` | `{module}/src/main/java/{basePackage}/service` |
| serviceImpl | `src/main/resources/templates/base/serviceImpl.java.ftl` | `{module}/src/main/java/{basePackage}/service/impl` |

## 策略规则

- `MybatisPlusGeneratorConfigFactory.templateConfig()` 显式 `disable(TemplateType.CONTROLLER)`。
- `StrategyConfig` 只启用 entity、mapper、service/serviceImpl。
- `IdType` 使用 `ASSIGN_ID`，避免生成器依赖数据库自增。
- 审计字段填充只声明 `create_time`、`create_user_id`、`update_time`、`update_user_id`。
- 表前缀、模块、包路径由 `table-module-map.yml` 和 `GeneratorModuleMappings` 统一维护。
- mapper XML 输出到各模块 `src/main/resources/mapper/base`，不混入 Java 包目录。

## base 与 manage 边界

`base` 包只承载贴表基础代码，供后续 `manage` 层组合使用。对外接口、BO/DTO/VO、权限、事务、幂等、字段权限、数据范围和流程状态校验必须由后续业务任务在 `manage` 层实现。

## 自检要求

后续 GEN-004 生成执行时必须检查：

1. 输出路径不包含 `controller` 或 `manage`。
2. 生成结果不包含旧包名。
3. 各模块只出现 `base/entity`、`base/mapper`、`base/service`、`base/service/impl` 和 `resources/mapper/base`。
4. `mvn -pl examine-generator -am -DskipTests compile` 通过。
