# Unexamine 项目

本目录是依据 `docs/user_requirement.md`、使用 `template_base` 构建的唯一当前项目。产品源码与 AI 开发控制面严格分开。

```text
unexamine/
├─ backend/        Java 21 / Spring Boot 后端；各模块 base 为生成代码，manage 为业务代码
├─ frontend/       Vue 3 Web 管理端与运行台
├─ db/             init、update、final SQL 及 Flyway 输入
├─ deploy/         本地依赖与后续部署文件
├─ tools/          项目生成参数和一键脚本
└─ ai/             需求、架构、任务、角色、决策、状态与周期证据
```

## 当前状态

项目尚未完成整站可用验收。当前执行断点以 `ai/planning/spark-state.json` 为准，局部编译和测试结果不能代替完整业务流程验证。

2026-10-09 按用户重新确认的产品主线复核完整需求和现有源码。当前保留、重写、删除判断及先定表、生成 Base、再贯通业务的顺序，见 `ai/planning/spark-execution-plan.md` 开头。旧复评和旧周期记录不再作为实施依据。

原 G1/G2/G3 旅程曾有局部证据，但新的系统所属权、负责人权限、租户共享和应用授权规则使它们需要按当前产品模型重新验收。当前整站交付状态为未验证；旧事实记录只能用于定位已有代码，不能算作现行需求通过。

首次空库启动会幂等创建默认平台超级管理员，部署时必须通过 `APP_BOOTSTRAP_DEFAULT_ADMIN_DEPLOYMENT_KEY` 提供至少 24 个字符的一次性初始化密钥；密钥不写入数据库、日志或验收证据。已有默认管理员时不会重复创建或重置密码。

## 数据库与代码生成

数据库设计只从 `db/init/` 和 `db/update/` 的版本 SQL 进入：

```powershell
python template_base/tools/tb.py db-prepare --project-root projects/unexamine
powershell -File projects/unexamine/tools/regenerate-base.ps1
```

第一条命令合成 `init.sql`、`update.sql`、`final.sql` 和 Flyway 输入；第二条命令执行 SQL、从真实 MySQL 元数据调用 MyBatis-Plus Generator，全量替换每个模块的代码包 `base`，并验证 `manage` 未被修改。开发期字段变化需保持 SQL、生成代码和业务代码一致；测试数据与表结构可以按当前需求重建，但不能把重新生成当成业务验收。

## 校验

```powershell
python template_base/tools/tb.py workspace-validate --workspace workspace.json
python template_base/tools/tb.py starter-check --starter projects/unexamine/tools/project-starter.json
```

本地环境只用于当前周期真实入口验收；端口、账号和样例业务数据由对应周期证据记录，不在项目根目录保留会失真的固定演示账号。
