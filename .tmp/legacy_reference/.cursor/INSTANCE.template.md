# 工程实例配置

本文件在实例化时复制为 `INSTANCE.md`。所有占位项必须填写或明确写 `not-applicable` 及原因。

## 1. 实例身份

- 实例名称：`{{INSTANCE_NAME}}`
- 项目名称：`{{PROJECT_NAME}}`
- 项目目标：`{{PROJECT_OBJECTIVE}}`
- 模板版本：`{{TEMPLATE_VERSION}}`
- 实例化时间：`{{INSTANTIATED_AT}}`

## 2. 路径绑定

| 逻辑名称 | 项目实际路径 | 用途 |
|---|---|---|
| 工程实例根 | `{{ENGINEERING_ROOT}}` | 角色、技能、流程、状态和工程产出 |
| 项目输入根 | `{{PROJECT_INPUT_ROOT}}` | 原始需求、配置、原型和参考资料 |
| 工程产出根 | `{{ENGINEERING_OUTPUT_ROOT}}` | 需求理解、设计、任务、评审和证据 |
| 服务端源码根 | `{{BACKEND_ROOT}}` | 服务端实现 |
| 前端源码根 | `{{FRONTEND_ROOT}}` | 前端实现 |
| 数据库资产根 | `{{DATABASE_ROOT}}` | 数据设计、迁移和生成配置 |
| 测试与证据根 | `{{EVIDENCE_ROOT}}` | 测试、运行、截图、日志和验收记录 |
| 发布资产根 | `{{RELEASE_ROOT}}` | 可交付包、部署和回滚资产 |

## 3. 权威输入

| 优先级 | 路径 | 内容 | 是否主动读取 | 冲突处理 |
|---|---|---|---|---|
| 1 | `{{SOURCE_PATH}}` | `{{SOURCE_PURPOSE}}` | `yes/no` | `{{CONFLICT_RULE}}` |

未列入本表的历史文件、聊天记录和旧实现不是默认事实源。

## 4. 技术与交付约束

- 技术栈：`{{TECH_STACK}}`
- 数据与代码生成边界：`{{GENERATION_BOUNDARY}}`
- 身份、权限与数据隔离：`{{SECURITY_BOUNDARY}}`
- 运行和部署方式：`{{RUNTIME_AND_DEPLOYMENT}}`
- 编码与记录要求：`{{ENCODING_AND_RECORDS}}`
- 最终交付形式：`{{DELIVERY_FORM}}`

### 交付节奏

- 项目基线最长：`240` 分钟。
- coding task 目标估算：`10..120` 分钟，推荐 `45..90`；超过时按模块内可验证结果继续拆分。
- 功能批次目标 `240` 分钟，任务数量按 DAG 决定；无依赖且写集不重叠的任务默认并行。
- task 只验证受影响层，slice 验证真实入口，phase/release 执行全量回归、重启和浏览器矩阵。
- 功能开发使用参考桌面视口；完整响应式、视觉和无障碍统一进入 release 前 hardening。

## 5. 启用角色

| 角色 | 启用 | 项目增量文件 | 说明 |
|---|---:|---|---|
| leader | yes | `agents/leader/update.md` | 总控 |
| pm | yes | `agents/pm/update.md` | 项目管理 |
| product | `{{YES_NO}}` | `agents/product/update.md` | 产品 |
| analyst | `{{YES_NO}}` | `agents/analyst/update.md` | 需求分析 |
| architect | `{{YES_NO}}` | `agents/architect/update.md` | 架构 |
| uiux | `{{YES_NO}}` | `agents/uiux/update.md` | 设计 |
| planner | `{{YES_NO}}` | `agents/planner/update.md` | 任务规划 |
| dba | `{{YES_NO}}` | `agents/dba/update.md` | 数据 |
| backend | `{{YES_NO}}` | `agents/backend/update.md` | 服务端 |
| frontend | `{{YES_NO}}` | `agents/frontend/update.md` | 前端 |
| test | yes | `agents/test/update.md` | 独立验收 |
| ops | `{{YES_NO}}` | `agents/ops/update.md` | 发布运维 |

## 6. 运行状态入口

- 状态唯一事实源：`{{STATE_PATH}}`
- 当前节点说明：`{{CURRENT_NODE_PATH}}`
- 最终目标和分期路线：`{{DELIVERY_ROADMAP_PATH}}`
- 全项目进度与打包契约：`templates/progress-package-contract.md`
- 权威进度 JSON 模板：`session/project-progress.template.json`
- 只读进度 Markdown 模板：`session/PROJECT_PROGRESS.template.md`

本配置只绑定稳定的项目身份、路径和特殊约束，不复制当前阶段、任务、Gate 或阻断范围。所有动态状态只在状态和当前节点文件更新。

## 7. 用户专属决策

| 决策类型 | 决策人 | 所需材料 | 待决/答案位置 |
|---|---|---|---|
| `{{DECISION_TYPE}}` | `{{OWNER}}` | `{{REVIEW_PACKAGE}}` | `{{DECISION_PATH}}` |

## 8. 项目特殊规约

公共角色契约位于 `agents/{role}/role.md`。项目特殊内容只写入同目录 `update.md`，包括项目路径、技术限制、特殊输入输出、额外 Gate 和禁止项。
