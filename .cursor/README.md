# unexamine 协作架构 v3

本目录是当前项目唯一协作规范来源。旧实现、旧脚本、旧 release、旧 evidence 已归档到 `.oldbk/restart-20260708-220451/`，只读参考，不再作为完成证据。

## 当前模式

`clean-rebuild`

目标：基于 `docs/user_requirement.md`、`docs/design/prototypes/**`、`temp_flow.md`、`temp_flow_persion.html` 重新梳理系统边界、规划任务、重新编码并验收，直到形成真实可使用的系统。

## 启动顺序

每次开始新任务前，先按落盘文件压缩上下文：

1. 读 `.cursor/session/state.json`
2. 读 `.cursor/architecture/clean-rebuild.md`
3. 读 `.cursor/knowledge/agent-operating-rules.md`
4. 读 `.cursor/knowledge/project-operating-rules.md`
5. 读 `.cursor/knowledge/failure-lessons.md`
6. 读 `.cursor/session/rebuild/source-index.md`
7. 读 `.cursor/session/rebuild/module-boundary-map.md`
8. 读 `.cursor/session/rebuild/task-plan.md`
9. 执行 `.cursor/workflows/clean-rebuild.md`

不得依赖旧聊天长上下文；新的用户纠偏必须先写回 `.cursor` 或 retained source 派生文件，再继续实现。

## 保留来源

- `docs/user_requirement.md`
- `docs/user_setting.md`
- `docs/design/prototypes/**`
- `temp_flow.md`
- `temp_flow_persion.html`

## 归档边界

- `.oldbk/restart-20260708-220451/` 是旧实现参考区。
- 旧 `RECOVERY-R*`、旧 release、旧 scripts、旧 docs evidence 不能作为 clean rebuild 完成证据。
- 可以参考旧代码的技术栈、已踩坑和局部写法，但必须按 clean rebuild 架构重新落地。

## 产品硬边界

- `工作台`：上下文统计；平台看平台统计，系统内看系统统计，可由后台配置。
- `Flow`：配置工作流程，可关联内部业务模块和外部第三方系统；对外服务必须经过应用授权。
- `应用`：系统内应用通信和对外服务的授权阀门；Flow 或系统服务要对外提供能力，必须先创建应用并关联授权范围。
- `工作`：工作统计、项目任务、普通任务、日报。
- `AI`：系统内 AI 分析和辅助扩展入口；配置在后台管理。
- `待办`：由后台配置驱动的提醒、审批、今日需回复等动作工作台。
- `消息`：与我相关的 @、审核、抄送、导入导出和主要操作提醒。
- `后台`：基础信息、组织、角色、模块、流程、应用、仪表盘、字典、工作、AI、数据源、日志等配置入口。
- `个人信息`：个人资料、切换系统、退出。

## UI 硬规则

- 列表主界面采用左侧标签页/左侧分类样式。
- 详情采用右侧标签页/右侧工作区样式。
- 行点击打开详情；行内按钮只承载编辑、删除、审批、导出等差异动作。

## 验收硬规则

- 真实完成必须覆盖前端、后端、数据持久化、权限、状态、读回、消息/待办/日志副作用和浏览器体验。
- API 200、生成 CRUD、静态页面、构建通过都只能作为局部证据。
- `gates.user_script_passed=true` 只能来自用户试用或签字，不能由脚本替代。
