# Unexamine 当前实现

`docs/user_requirement.md` 是产品需求。`docs/表意义.md` 是表结构参考，`docs/old_table1.sql` 只供早期审批关系设计参考。当前数据库使用已有 Docker MySQL 的隔离库 `unexamine_r15`，已有 Docker Redis 用于登录会话。

从工作区根目录运行 `& projects/unexamine/tools/run-local.ps1`。页面地址是 <http://127.0.0.1:15173/>，后端固定监听 `9999`。首次使用可在登录页注册账号。运行脚本只连接已有容器，不创建 MySQL、Redis、Nginx 或 Compose 服务。

现有页面已接通：注册登录 → 创建系统和主租户 → 创建模块组、模块和常用字段 → 配置现有页面动作与列表菜单名称 → 发布模块 → 配置角色菜单权限、添加已有平台账号 → 受邀成员录入、筛选、查看和编辑本人负责的记录 → 所属人为模块配置并发布单节点审批 → 成员发起，指定审批人查看待办并同意或拒绝 → 审批人与申请人在当前系统消息中收到待办和结果通知。字段现支持文本、多行文本、手机号、邮箱、网址、数字、金额、百分比、日期、日期时间、开关和单选；其中多行文本暂不支持精确筛选，百分比按 0–100 录入。动作和菜单草稿要重新发布才改变运行态；关闭的动作在页面与 API 同时失效，角色授权不能绕过。审批发布版本与运行实例隔离；拒绝后可修改记录重新发起。消息只显示通用标题，打开待办或记录仍按实时权限校验。业务记录现可上传、预览、下载及移除附件；模块可按当前筛选和独立导出权限创建异步 CSV 任务并下载。Excel 导入支持当前发布字段模板、逐行预检查、错误文件、确认后整批新增和按系统记录编号更新；错误文件首表可修正后重传。自定义唯一字段匹配、关联/子表等高级字段、复杂流程、其他消息来源、应用访问、移动端和完整部署包仍未实现；这条基础旅程不等于整个产品交付。

本地文件默认保存在 `projects/unexamine/data/files`，导出结果默认保存在 `projects/unexamine/data/exports`，导入源文件默认保存在 `projects/unexamine/data/imports`，均被 Git 忽略。需要调整时分别设置 `APP_FILE_ROOT`、`APP_EXPORT_ROOT`、`APP_IMPORT_ROOT` 为服务进程可写的绝对路径。单个附件上限 10 MB；图片和 PDF 可以在登录状态下预览，其他类型只提供下载。导出最多 10000 行，Excel 导入单次最多 1000 行和 2 MB，任务状态和失败原因在模块页面查看。

新增或修改表先写入 `db/init/` 或 `db/update/`，运行 `python template_base/tools/tb.py db-prepare --project-root projects/unexamine`，再运行 `& projects/unexamine/tools/regenerate-base.ps1` 更新 MyBatis-Plus 生成的 `base`。手写业务代码放在各模块的 `manage`，页面放在 `frontend/src/views`。按 `ai/planning/rebuild-state.json` 中当前步骤继续，验收证据位于 `ai/evidence/`。
