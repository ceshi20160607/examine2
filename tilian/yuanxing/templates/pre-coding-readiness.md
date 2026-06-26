# 开发前可交接验收矩阵

> 本文是证据索引，不是新的需求来源。

## 1. 当前结论

- 原型版本：
- brief：
- 原型入口：
- 用户签字：

## 2. 只读入口

| 类型 | 文件 |
|---|---|
| 原始需求 | `docs/user_requirement.md` |
| 原型输入 | `docs/design/prototype-brief.md` |
| 当前原型 | `docs/design/prototypes/index.html` |
| 原型复审 | `docs/design/prototype-review.md` |
| 静态审查 | `docs/evidence/prototype-audit-YYYY-MM-DD.md` |

## 3. 证据矩阵

| 能力 | 开发前必须看到 | 证据位置 |
|---|---|---|
| 角色入口 | 登录、默认首页、权限裁剪 |  |
| 核心列表 | 搜索、筛选、排序、分页、列设置、批量限制 |  |
| 详情 | 摘要、tab、附件、日志、审批状态 |  |
| 表单 | 必填、校验、草稿、失败定位 |  |
| 后台配置 | 字段、字典、流程、角色、页面动作 |  |
| 异步任务 | taskId、状态、结果、错误、traceId |  |
| 权限 | 数据范围、字段权限、按钮权限、无权限反馈 |  |

## 4. 已执行验证

| 验证 | 结果 |
|---|---|
| 静态断链 |  |
| default/generic 兜底 |  |
| 关键按钮结果 |  |
| 多角色复审 |  |
| 浏览器主剧本 |  |

## 5. 签字后行动

用户确认后，将 `docs/design/user-approval.md` 改为 `approved: true`，再进入 API 契约冻结。未签字前，不创建 backend/frontend/sql 实现任务。
