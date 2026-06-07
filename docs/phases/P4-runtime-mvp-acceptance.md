# P4 runtime-mvp 阶段验收记录

更新时间：2026-06-07

## 验收结论

P4-runtime-mvp 阶段通过 PM 阶段验收，允许进入 P5-workflow-files-openapi。

本阶段完成运行台 MVP 的后端运行态记录 API 与前端页面模型联调闭环。后端已提供运行台菜单、模块运行态 schema、动态记录查询、新增、详情、更新、软删除、提交、历史和关联关系查询；前端已补齐运行台页面模型、动态表单校验、记录状态禁用规则、字段级错误回填和页面契约证据。

## 完成范围

| 范围 | 结论 | 说明 |
| --- | --- | --- |
| BE-008 运行台记录 CRUD 与动态数据 API | pass | RUN-001 至 RUN-010 后端入口完成，包含 schema、记录分页、保存、详情、更新、软删除、提交、历史和关联查询。 |
| FE-008 运行台页面与动态表单联调 | pass | 运行台菜单、记录列表、动态表单、详情、保存、编辑、删除、提交、历史和关系查询页面模型完成。 |

## 验证结果

| 验证项 | 结果 |
| --- | --- |
| 后端 BE-008 | `mvn -pl examine-module -am -DskipTests compile` 通过。 |
| 后端测试 | `mvn -pl examine-module -am test` 通过：core 12、plat 12、module 18 个测试。 |
| 前端 FE-008 | 当前 `frontend/` 无 `package.json` 和 `tsconfig.json`，无法执行正式 clean build/typecheck；已完成静态页面模型与契约证据。 |
| 静态检查 | `git diff --check` 通过，仅提示 Git 工作区 LF/CRLF 转换 warning。 |

## 遗留与 P5 入口

1. RUN-008 提交流程当前由 BE-008 记录状态占位：已绑定流程进入 `IN_APPROVAL`，未绑定流程进入 `SUBMITTED`；P5 的 BE-009 需要接入真实流程实例、待办、审批动作和流程历史。
2. 文件、导出、OpenAPI、审计运维仍属于 P5/P6 范围，不在 P4 验收范围内。
3. 前端正式工程化入口缺失仍保留到 P6 最终验收处理；在补齐 `package.json` 和 `tsconfig.json` 后，必须执行 clean build/typecheck。
