# Unexamine 当前实现

`docs/user_requirement.md` 是产品需求。`docs/表意义.md` 是表结构参考，`docs/old_table1.sql` 只供早期审批关系设计参考。当前数据库使用已有 Docker MySQL 的隔离库 `unexamine_r15`，已有 Docker Redis 用于登录会话。

从工作区根目录运行 `& projects/unexamine/tools/run-local.ps1`。页面地址是 <http://127.0.0.1:15173/>，后端固定监听 `9999`。首次使用可在登录页注册账号。运行脚本只连接已有容器，不创建 MySQL、Redis、Nginx 或 Compose 服务。

现有页面已接通：注册登录 → 创建系统和主租户 → 创建模块组、模块和基础字段 → 发布模块 → 配置角色菜单权限、添加已有平台账号 → 受邀成员录入、筛选、查看和编辑本人负责的记录 → 所属人为模块配置并发布单节点审批 → 成员发起，指定审批人查看待办并同意或拒绝。审批发布版本与运行实例隔离；拒绝后可修改记录重新发起。业务记录现可上传、预览、下载及移除附件；模块可按当前筛选和独立导出权限创建异步 CSV 任务并下载。高级字段、复杂流程、Excel 导入、应用访问、移动端、消息和完整部署包仍未实现；这条基础旅程不等于整个产品交付。

本地文件默认保存在 `projects/unexamine/data/files`，导出结果默认保存在 `projects/unexamine/data/exports`，两者均被 Git 忽略。需要调整时分别设置 `APP_FILE_ROOT`、`APP_EXPORT_ROOT` 为服务进程可写的绝对路径。单个附件上限 10 MB；图片和 PDF 可以在登录状态下预览，其他类型只提供下载。导出最多 10000 行，任务状态和失败原因在模块页面查看。

新增或修改表先写入 `db/init/` 或 `db/update/`，运行 `python template_base/tools/tb.py db-prepare --project-root projects/unexamine`，再运行 `& projects/unexamine/tools/regenerate-base.ps1` 更新 MyBatis-Plus 生成的 `base`。手写业务代码放在各模块的 `manage`，页面放在 `frontend/src/views`。按 `ai/planning/rebuild-state.json` 中当前步骤继续，验收证据位于 `ai/evidence/`。
