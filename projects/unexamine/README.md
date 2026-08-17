# Unexamine 项目

本目录是依据 `docs/user_requirement.md`、使用 `template_base` 构建的唯一当前项目。产品源码与 AI 开发控制面严格分开。

```text
unexamine/
├─ backend/        Java 21 / Spring Boot 后端；各模块 base 为生成代码，manage 为业务代码
├─ frontend/       Vue 3 Web 管理端与运行台
├─ db/             base、update、all SQL 及 Flyway 输入
├─ deploy/         本地依赖与后续部署文件
├─ tools/          项目生成参数和一键脚本
└─ ai/             需求、架构、任务、角色、决策、状态与周期证据
```

## 当前状态

正在重新完成“全量需求分析 → 唯一能力归属 → 原子用例/任务 → 现有代码评估 → 表结构 → 排期”。在原子任务和表结构冻结前，不预设阶段数或周期数。

在原子任务和依赖完成前，总阶段数、总周期数及总体百分比不可计算。现有 27 张表、108 个生成文件及登录/模块样例只是待逐项审计资产，不计为产品完成。机器可读状态见 `ai/status/project-status.json`。

## 数据库与代码生成

数据库设计只从 `db/base/` 和 `db/update/` 的版本 SQL 进入：

```powershell
python template_base/tools/tb.py db-prepare --project-root projects/unexamine
powershell -File projects/unexamine/tools/regenerate-base.ps1
```

第一条命令合成 `base.sql`、`update.sql`、`all.sql` 和 Flyway 输入；第二条命令执行 SQL、从真实 MySQL 元数据调用 MyBatis-Plus Generator，全量替换每个模块 `base`，并验证 `manage` 未被修改。

## 校验

```powershell
python template_base/tools/tb.py workspace-validate --workspace workspace.json
python template_base/tools/tb.py starter-check --starter projects/unexamine/tools/project-starter.json
```

本地试用地址和启动命令见 `TRIAL.md`。
