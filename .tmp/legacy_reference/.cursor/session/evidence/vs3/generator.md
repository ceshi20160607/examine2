# VS3 Generator 验证证据

- checked_at: `2026-07-15T12:21:47+08:00`
- verdict: `pass`
- report: `backend/generator-reports/examine-module-un_module_.json`

## 结果

- module: `examine-module`
- table prefix: `un_module_`
- base package: `com.unique.examine.module`
- tables: `17`
- generated files: `85`（每表 entity、mapper、XML、IService、ServiceImpl）
- generated controllers: `0`
- entity `toString`: `0`
- generated writable columns leaked: `0`
- database: MySQL `8.4.10`
- report paths: project-relative; absolute generated file paths `0`

## 模块化修正

首次全应用启动发现 plat/module 同名 `PermissionServiceImpl` 和 `PermissionMapper` 会产生 Spring/MyBatis Bean 短名冲突。generator 已统一给新模块的 Mapper/XML/ServiceImpl 增加模块前缀，例如 `ModulePermissionMapper`、`ModulePermissionServiceImpl`；删除并重生成后全 reactor 通过。

CLI 在 JDBC 连接关闭后显式停止 MySQL abandoned-connection cleanup thread，后续 generator 执行不再产生 Maven lingering-thread 告警。base 仍完全由 generator 维护，manage 业务代码不进入生成目录。

CLI 在每批实际生成后仅对本次所属的 entity/mapper/XML/service 文件执行确定性文本规范化：移除行尾水平空白、移除多余 EOF 空行并保留单一终止换行。该规则修复 MyBatis-Plus 空表注释模板产生的 `* `，避免每次重生成重新污染暂存树。

输出文件不再通过目录后缀扫描猜测，而是按每张表精确构造 `entity + 模块前缀 Mapper + XML + IService + 模块前缀 ServiceImpl` 五个路径并检查文件存在。执行前删除同表早期无前缀 Mapper/XML/ServiceImpl；手写 manage 层统一依赖 `Core*Mapper`、`Plat*Mapper` 或 `Module*Mapper`，旧重复家族数量为 `0`。

generator 读取 `information_schema.COLUMNS.GENERATION_EXPRESSION` 并按每张表的真实生成列集合分批生成。同名 `target_id` 在 `un_module_config_reference` 是可写业务列，能够生成 `targetId`；该表的 16 个类型化引用投影列有生成表达式，保持只读排除。带 JSON 表达式默认值但没有生成表达式的会话快照列不会被误排除。

| prefix | tables | exact generated files |
|---|---:|---:|
| `un_audit_` | 2 | 10 |
| `un_sys_` | 4 | 20 |
| `un_plat_` | 25 | 125 |
| `un_module_` | 17 | 85 |
| 合计 | 48 | 240 |

四个前缀连续实际执行两轮；两轮 240 个 base 文件的内容摘要均为 `d0f1a9d146d74c57fcd89da38704de364890f34a8786eff7f880152c8951ede5`。第二轮后六模块编译和 8 个测试通过，最新 jar 重启健康，`git diff --cached --check` 为零问题。
