# BE-009 流程模板实例任务 API

- taskId: BE-009
- 标题: 流程模板实例任务 API
- 负责角色: backend
- 所属大任务/模块: 后端 / examine-flow
- 目标: 实现流程模板、发布、绑定、待办、实例、任务处理和业务状态联动接口。
- 输入文件: `docs/api.md`、`docs/db_design.md`、`backend/examine-flow/`、`backend/examine-module/`
- 输出文件或输出目录: `backend/examine-flow/`

## 详细工作内容

- 实现 FLOW-001 至 FLOW-021 的 MVP 接口。
- 保存结构化流程图、发布版本、模块绑定、实例、任务、抄送和历史。
- 实现同意、拒绝、转交、退回、终止、撤回、领取、取消领取和并发版本校验。

## 完成状态定义

- 当前状态: done。
- 完成条件: 流程工作台和运行台提交审批闭环可用。

## 完成记录

- 完成时间: 2026-06-07
- 输出:
  - `backend/examine-core/src/main/java/com/unique/examine/core/flow/`
  - `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/`
  - `backend/examine-flow/src/test/java/com/unique/examine/flow/manage/controller/FlowManageControllerTest.java`
  - `backend/examine-flow/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
  - `backend/examine-module/src/main/java/com/unique/examine/module/manage/service/impl/RuntimeRecordServiceImpl.java`
- 自检:
  - `mvn -pl examine-flow -am -DskipTests compile` 通过。
  - `mvn -pl examine-flow -am test` 通过：core 12、plat 12、module 18、flow 2 个测试。

## 验收标准

- 流程动作能同步推进实例、任务和业务记录状态。
- 重复处理或非候选人处理返回明确错误码。

## 测试/自检要求

- 测试无结束路径发布失败、任务并发处理、撤回、转交、退回、终止原因必填。

## 依赖任务

- BE-007
- BE-008
- BE-014

## 可并行关系

- 可与 BE-010 并行；BE-012 必须等待 BE-009 与 BE-010 都完成。

## 不允许事项

- 不只保存不可读 JSON。
- 不使用 fire-and-forget 异步参与需要回滚的审批事务。
