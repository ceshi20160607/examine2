# 原型 Harness 完整性扫描报告

> 日期：2026-06-23
> 范围：当前项目原型链路、`.cursor` 协作规则、`docs/design` 设计产物、`docs/api` 契约产物、`tilian/yuanxing` 提炼资产。

## 1. 扫描输入

本次重点读取和对照：

- `.cursor/README.md`
- `.cursor/session/state.json`
- `.cursor/knowledge/agent-operating-rules.md`
- `.cursor/knowledge/project-operating-rules.md`
- `.cursor/knowledge/failure-lessons.md`
- `.cursor/workflows/phase-1-internal-design-complete.md`
- `.cursor/workflows/phase-1-design-freeze.md`
- `docs/user_requirement.md`
- `docs/design/prototype-brief.md`
- `docs/design/design-package.md`
- `docs/design/reviews/prototype-latest-2026-06-18.md`
- `docs/design/pre-coding-readiness.md`
- `docs/design/prototypes/index.html`
- `tilian/yuanxing/yuanxing.md`

## 2. 原始提炼判断

原始 `yuanxing.md` 已经覆盖了原型阶段的核心经验，包括：

- 需求压缩。
- 先定架构再画页面。
- 对象和视图分离。
- 角色权限矩阵。
- 页面清单。
- brief 结构。
- 多角色复审。
- P0 阻断清单。
- 用户签字门禁。

但它还不能直接作为“粗需求生成丰满原型”的完整 Harness 使用，主要缺口是：

| 缺口 | 影响 |
|---|---|
| 仍把 Open Design 写成阶段执行点 | 和“不用 Open Design”的目标冲突 |
| 没有 prototype-builder 角色和生成标准 | 新项目不知道如何直接产出 HTML 原型 |
| 缺少可复制模板 | 只能读方法，不能快速落盘启动 |
| 缺少静态审查脚本 | 断链、泛化入口、toast、重复详情动作只能人工凭感觉查 |
| 缺少 prompts | 单人或多 agent 执行时角色边界不稳 |
| 缺少 state/gate 模板 | 容易跳过签字直接进入 API 或开发 |
| 缺少一次性扫描结论 | 后续读者不知道哪些内容来自本项目复盘 |

## 3. 已补齐内容

本次已将 `tilian/yuanxing` 补成可复制执行包：

- `README.md`：快速使用入口。
- `yuanxing.md`：改为不依赖 Open Design 的完整流程。
- `templates/state.json`：原型阶段 gate 模板。
- `templates/prototype-brief.md`：唯一原型输入模板。
- `templates/prototype-review.md`：多角色复审模板。
- `templates/pre-coding-readiness.md`：开发前证据矩阵模板。
- `templates/user-approval.md`：用户签字模板。
- `templates/prototype-audit.md`：静态审查证据模板。
- `prompts/prototype-conductor.md`：需求压缩和 brief 调度提示词。
- `prompts/prototype-builder.md`：直接生成静态 HTML 的构建提示词。
- `prompts/prototype-reviewer.md`：干净上下文复审提示词。
- `tools/prototype-static-audit.mjs`：零依赖静态审查脚本。

## 4. 当前使用结论

补齐后，这套目录可以作为完整的原型阶段 Harness 使用。推荐方式：

1. 复制整个 `tilian/yuanxing/` 到新项目。
2. 先落 `docs/user_requirement.md`。
3. 用 `prototype-conductor` 产出理解、角色矩阵、对象模型、页面契约和 brief。
4. 用 `prototype-builder` 直接生成 `docs/design/prototypes/index.html`。
5. 运行 `tools/prototype-static-audit.mjs`。
6. 用 `prototype-reviewer` 做干净上下文复审。
7. 无 P0/P1 后再让用户签字。

## 5. 验证结果

对当前项目真实原型执行：

```powershell
node tilian/yuanxing/tools/prototype-static-audit.mjs docs/design/prototypes/index.html
```

结果：

- `brokenRefs`: 0
- `hardFailures`: 0
- `warnings`: 0

同时执行：

```powershell
git diff --check -- tilian/yuanxing
```

结果：无空白错误。

## 6. 剩余边界

这套 Harness 负责“原型阶段”，不替代后续 API 契约、任务拆分、开发实现和 E2E 验收。

如果新项目不是 ToB、后台、低代码、管理系统或业务平台类产品，需要保留 gate、模板和审查方法，但可调整 P0 阻断清单里的后台配置、权限、流程、日志等具体条目。
