# VAL-002 前端 clean build

- taskId: VAL-002
- 标题: 前端 clean build
- 负责角色: validator
- 所属大任务/模块: 构建验证
- 目标: 清理前端旧构建产物后执行 clean build。
- 输入文件: `frontend/`、`docs/service_info.md`
- 输出文件或输出目录: `docs/build/frontend-clean-build.md`

## 详细工作内容

- 删除 `dist`、`tsconfig.tsbuildinfo` 等旧构建产物后重新 build。
- 记录实际 Node/npm 路径和版本。
- 检查源码目录没有混入 `.vue.js`、临时 `.d.ts` 或编译产物，并写入 `docs/build/frontend-clean-build.md`。

## 完成状态定义

- 默认状态: pending。
- 完成条件: 前端 clean build 结果写入 `docs/build/frontend-clean-build.md`，最终 `docs/build_report.md` 只由 VAL-004 汇总写入。

## 验收标准

- 构建从干净状态开始。
- 失败日志能定位类型、依赖或构建配置问题。

## 测试/自检要求

- 执行前端 clean build 命令。

## 依赖任务

- FE-012

## 可并行关系

- 可与 VAL-001、VAL-003 并行。

## 不允许事项

- 不修改前端源码。
- 不使用旧 dist 作为通过依据。
