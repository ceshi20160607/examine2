# 工程实例配置

本文件在实例化时复制为 `INSTANCE.md`。所有占位项都必须由目标项目填写。

## 身份

- 实例名称：`{{INSTANCE_NAME}}`
- 项目名称：`{{PROJECT_NAME}}`
- 模板版本：`{{TEMPLATE_VERSION}}`
- 实例化时间：`{{INSTANTIATED_AT}}`

## 路径绑定

| 逻辑名称 | 项目实际路径 | 用途 |
|---|---|---|
| 工程实例根 | `{{ENGINEERING_ROOT}}` | 角色、技能、流程、状态和工程产出 |
| 项目输入根 | `{{PROJECT_INPUT_ROOT}}` | 需求、配置、原型和其他人类可读输入 |
| 服务端源码根 | `{{BACKEND_ROOT}}` | 服务端实现 |
| 前端源码根 | `{{FRONTEND_ROOT}}` | 前端实现 |
| 数据库资产根 | `{{DATABASE_ROOT}}` | 模型、迁移和生成配置 |
| 测试与证据根 | `{{EVIDENCE_ROOT}}` | 自动化结果、截图、日志和验收记录 |

不存在的目录必须写 `not-applicable` 并说明原因，不能保留未填写占位项。

## 项目输入

列出本项目保留的权威输入及优先级：

| 优先级 | 路径 | 内容 | 冲突处理 |
|---|---|---|---|
| 1 | `{{SOURCE_PATH}}` | `{{SOURCE_PURPOSE}}` | `{{CONFLICT_RULE}}` |

## 技术约束

- 技术栈：`{{TECH_STACK}}`
- 数据与代码生成边界：`{{GENERATION_BOUNDARY}}`
- 运行和部署约束：`{{RUNTIME_CONSTRAINTS}}`
- 编码与记录约束：`{{RECORD_CONSTRAINTS}}`

## 当前工程节点

- 当前阶段：`{{CURRENT_PHASE}}`
- 当前节点：`{{CURRENT_NODE}}`
- 当前目标：`{{CURRENT_OBJECTIVE}}`
- 下一 Gate：`{{NEXT_GATE}}`
- 阻断范围：`{{BLOCKED_SCOPE}}`

## 项目特殊规约

公共角色契约位于 `agents/{role}/role.md`。项目有额外要求时，在同一角色目录创建 `update.md`，只写增量规则，不复制公共角色全文。

## 用户决策权

列出只能由用户、业务负责人或指定签字人决定的事项：

| 决策 | 决策人 | 所需材料 | 记录位置 |
|---|---|---|---|
| `{{DECISION}}` | `{{OWNER}}` | `{{REVIEW_PACKAGE}}` | `{{RECORD_PATH}}` |

