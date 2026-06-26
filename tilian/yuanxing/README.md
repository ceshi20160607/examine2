# yuanxing 原型生成 Harness

这是一套从粗需求生成可开发原型的可复制流程，不依赖 Open Design。

## 快速使用

1. 把 `tilian/yuanxing/` 整个目录复制到新项目。
2. 建立 `docs/user_requirement.md`，保留用户原始需求。
3. 用 `prompts/prototype-conductor.md` 生成需求理解、角色矩阵、对象模型和页面契约。
4. 用 `templates/prototype-brief.md` 组织唯一原型输入。
5. 用 `prompts/prototype-builder.md` 直接生成 `docs/design/prototypes/index.html`。
6. 运行静态审查：

```powershell
node tilian/yuanxing/tools/prototype-static-audit.mjs docs/design/prototypes/index.html
```

7. 用 `prompts/prototype-reviewer.md` 做干净上下文复审，并把结果写入 `docs/design/prototype-review.md`。
8. 无 P0/P1 后才让用户在 `docs/design/user-approval.md` 签字。

## 文件说明

- `yuanxing.md`：完整方法和门禁。
- `templates/`：可复制到新项目的基础模板。
- `prompts/`：角色提示词。
- `tools/prototype-static-audit.mjs`：零依赖静态审查脚本。

## 判断标准

只有同时满足以下条件，才算“可直接进入下一阶段”：

- `prototype-brief.md` 能独立说明产品、角色、对象、页面和验收。
- `index.html` 可点击，并能走完主要用户剧本。
- 静态审查无断链、无 P0 兜底入口。
- 多角色复审无 P0/P1。
- 用户签字为 `approved: true`。
