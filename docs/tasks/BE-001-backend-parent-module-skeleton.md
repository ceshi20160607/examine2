# BE-001 后端父子 POM 与模块骨架

- taskId: BE-001
- 标题: 后端父子 POM 与模块骨架
- 负责角色: backend
- 所属大任务/模块: 后端架构
- 目标: 建立参考 PRD 的 Maven 多模块后端工程骨架。
- 输入文件: `docs/prd.md`、`docs/api.md`、`docs/db_design.md`、`sql/init.sql`、`docs/service_info.md`
- 输出文件或输出目录: `backend/`

## 详细工作内容

- 创建父工程和 `examine-core`、`examine-plat`、`examine-module`、`examine-flow`、`examine-upload`、`examine-app`、`examine-generator`、`examine-web` 模块。
- 在 `examine-web` 放启动类、Web 装配和全局入口。
- 建立各业务模块 `base` 与 `manage` 包边界。

## 完成状态定义

- 默认状态: pending。
- 完成条件: Maven 父子模块结构完整，后续模块能按依赖继续开发。

## 验收标准

- `examine-web` 不堆业务实现。
- 各业务模块包结构支持 base/manage 分层。

## 测试/自检要求

- 运行基础 Maven 模块识别或 compile 自检。

## 依赖任务

- DBA-006

## 可并行关系

- 完成后 BE-002 与 GEN-001 可并行；BE-003、BE-014 必须等待 BE-002 完成。

## 不允许事项

- 不私自修改冻结 API。
- 不手写大批量贴表 CRUD 替代生成器。
