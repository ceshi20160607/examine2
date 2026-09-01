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

需求、架构、用例、任务、依赖、现有代码评估、数据库、Base、后端 `manage`、前端、运维和真实入口验收均已完成并通过机器校验。184 条原子需求归属到 20 个能力和 60 个真实业务用例，拆为 191 个任务、6 个依赖阶段和 56 个最多 240 分钟的周期。

首次数据库基线由 18 个有序片段组成，共 150 张固定能力及通用运行时表；当前 V34 由 34 个迁移组成，共 155 张业务表，MyBatis-Plus Generator 已从真实 MySQL 元数据生成 18 个模块、612 个 `base` 文件，并通过重复生成与后端编译。56/56 个连续周期均有完整验收证据，9 个质量关口已完成；机器可读事实见 `ai/status/project-status.json` 和 `ai/evidence/CYCLE-*.json`。

首次空库启动会幂等创建默认平台超级管理员，部署时必须通过 `APP_BOOTSTRAP_DEFAULT_ADMIN_DEPLOYMENT_KEY` 提供至少 24 个字符的一次性初始化密钥；密钥不写入数据库、日志或验收证据。已有默认管理员时不会重复创建或重置密码。

## 数据库与代码生成

数据库设计只从 `db/init/` 和 `db/update/` 的版本 SQL 进入：

```powershell
python template_base/tools/tb.py db-prepare --project-root projects/unexamine
powershell -File projects/unexamine/tools/regenerate-base.ps1
```

第一条命令合成 `init.sql`、`update.sql`、`final.sql` 和 Flyway 输入；第二条命令执行 SQL、从真实 MySQL 元数据调用 MyBatis-Plus Generator，全量替换每个模块的代码包 `base`，并验证 `manage` 未被修改。首次 Base 已生成，后续开发期字段变化必须新增到 `db/update/`，不能回写已冻结的首次基线。

## 校验

```powershell
python template_base/tools/tb.py workspace-validate --workspace workspace.json
python template_base/tools/tb.py starter-check --starter projects/unexamine/tools/project-starter.json
```

本地环境只用于当前周期真实入口验收；端口、账号和样例业务数据由对应周期证据记录，不在项目根目录保留会失真的固定演示账号。
