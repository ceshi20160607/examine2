# TEST-002 API 契约用例

- taskId: TEST-002
- 标题: API 契约用例
- 负责角色: test
- 所属大任务/模块: 测试 / API
- 目标: 设计冻结 API 的正常、异常、权限、边界、幂等和并发用例。
- 输入文件: `docs/api.md`、`docs/test_plan.md`
- 输出文件或输出目录: `docs/test_plan.md`

## 详细工作内容

- 按 AUTH、PLAT、SYS、MEM、RBAC、DICT、APP、MOD、FIELD、UI、RUN、FLOW、FILE、EXP、OPM、OPN、AUD、OPS 分组设计用例。
- 覆盖统一响应、错误码、`errors[]`、requestId、分页、筛选、动态字段和状态流转。
- 标明自动化优先级和数据前置条件。

## 完成状态定义

- 默认状态: pending。
- 完成条件: API 用例可直接指导接口测试。

## 验收标准

- 每类接口至少有正常、异常、权限、边界和幂等/并发测试点。

## 测试/自检要求

- 自检用例覆盖 API 文档中的 MVP 接口。

## 依赖任务

- TEST-001

## 可并行关系

- 可与后端和前端实现准备并行。

## 不允许事项

- 不基于未冻结接口设计用例。
- 不把页面视觉检查替代 API 契约断言。

