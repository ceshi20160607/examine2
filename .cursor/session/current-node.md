# CYCLE-FINAL-DELIVERY-118：冻结目标恢复与真实缺口闭环

## 1. 周期身份

- cycle_id: `CYCLE-FINAL-DELIVERY-118`
- cycle_start_at: `2026-08-07T18:24:00+08:00`
- delivery_cycle_minutes: `240`
- status_ref: `.cursor/session/state.json#status`
- owner: `pm`
- integration_owner: `root`
- formal_checkpoint_limit: `3`
- formal_checkpoint_used: `2`

## 2. 纠偏结论

Cycle117 的应用测试、依赖修复、空库安装和 8.78→8.91 升级证据仍然有效，但其最终发布结论已撤回。源码级逐旅程审计证明旧阶段验收把局部功能错误外推为完整目标，并且旧 ZIP 缺稳定运维脚本和角色文档。

权威进度先由 `97.8%` 重算为 `71.1%`；完成平台缺口和交付恢复闭环后现为 `94.3%`：`142 / 146` outcomes、`232 / 246` weighted units 完成。`integrationPassed=false`、`releasePassed=false`、`userAccepted=false`。CP1/CP2 保留为历史尝试，CP3 保持 `not_ready` 且 `0` 次尝试；第 3 次正式尝试只允许用于全部缺口关闭后的唯一候选包。

## 3. 恢复工作图

| outcome | 单元 | 状态 | 边界 |
|---|---:|---|---|
| `GAP_PLATFORM_APPLICATION_EXTERNAL_API` | 7 | completed | 平台应用管理、SecretRef/轮换、平台 scope HMAC API 与调用日志 |
| `GAP_ONBOARDING_COMMAND_CENTER` | 4 | completed | 最低配置→成员预览→发布检查；菜单/系统/模块/待办/记录命令中心 |
| `GAP_UNIFIED_AUDIT_OBSERVABILITY` | 5 | completed | 平台/系统统一日志、trace 查询和依赖体检 |
| `GAP_DELIVERY_RECOVERY_DOCUMENTATION` | 5 | completed | PowerShell/POSIX 资产、角色手册及真实备份/恢复/重启/升级/回滚演练 |
| `GAP_PLATFORM_FLOW_DASHBOARD` | 12 | completed | 平台 Flow、运行仪表盘与平台仪表盘配置 |
| `GAP_PLATFORM_WORK_TODO_MESSAGE` | 10 | completed | 平台项目/任务/日报/日历、多来源待办与独立消息收件箱 |
| `GAP_SSO_MFA_PROFILE_SECURITY` | 4 | completed | 终端 SSO/MFA、个人资料、恢复码与会话管理 |
| `GAP_PLATFORM_LIFECYCLE_RECOVERY_SETTINGS` | 5 | completed | 初始化失败恢复、平台信息及全局存储/安全/配额/备份策略 |
| `GAP_CONFIG_DOMAIN_ROLLBACK` | 5 | completed | dashboard/data source/KPI/report/print 等配置域可执行恢复 |
| `GAP_PERFORMANCE_CAPACITY_GATE` | 6 | in_progress | 百万记录、100/20 VU、Q01-Q12、HDR、EXPLAIN、浏览器 P75 |
| `GAP_A11Y_USABILITY_FINAL` | 4 | remaining | axe 与键盘/焦点切片已 PASS；仍需普通成员完整角色旅程 |

不同模块写集并行；数据库迁移已固定：`V8_92` 平台 OpenAPI、`V8_93` 统一日志权限、`V8_94` 平台工作/待办/消息、`V8_95` 平台 Flow/仪表盘、`V8_96` 平台生命周期/设置。Maven reactor 仍通过单一 Java 21 窗口串行执行，避免共享 target 和数据库冲突。

## 4. 当前已形成但尚未正式验收的输出

- `delivery/` 已具备 Compose/Nginx、`.env.example`、11 个 PowerShell 脚本、11 个 POSIX shell 脚本和 10 篇交付文档。
- `.cursor/scripts/validate-delivery-package.ps1` 的 source gate 已通过：35 个必需条目、PowerShell AST、Bash 语法和文档合同。
- `build-cycle-package.ps1` 已支持复制完整交付结构、生成 `VERSION.json`、区分 rolling/formal 包并记录 formal checkpoint 状态。
- 最近一次全量功能门已通过：前端 `150 files / 722 tests`、typecheck、production build；后端累计 `547` 测试类、`1814` tests、零失败/错误/跳过；最终源码冻结后仍会重跑一次全量门。
- Cycle118 交付演练已通过：备份、校验、破坏性恢复、重启、升级、回滚和再次升级均健康，恢复后读回验收系统 `1` 条与 Flyway `117` 条。
- 桌面 7 个页面及移动端 6 个页面的 axe critical/serious 为 `0`；跳转主内容、命令中心搜索框初始焦点和 Escape 焦点归还均通过。
- 性能目录已经具备确定性百万数据生成/校验、Q01-Q12、100/20 VU、HDR、MySQL 查询计划与浏览器 P75 采集器；这些资产必须在实际运行包和正式容量数据上执行，脚本存在不等于性能 PASS。

## 5. 完成与停止规则

1. 每个 gap 必须有真实入口、owner API/数据、允许与拒绝测试、结果读回和独立 acceptance evidence；只写文档或只做 mock 不算完成。
2. 性能、可访问性、恢复/回滚必须实际运行；存在脚本不等于 gate 通过。
3. 所有 57 个 REQ、52 个 JRN、8 个 NFR 必须进入项目 final-goal/UAT ledger，不能再用一个聚合 journey 代替。
4. 应用源码冻结后执行后端全量、前端全量、生产构建、迁移、clean install、upgrade、backup/restore/rollback、安全和真实浏览器旅程。
5. 上述全部通过后才生成 Cycle118 唯一正式包并消耗 CP3；工程最终 PASS 仍不能代替用户 sign-off。
