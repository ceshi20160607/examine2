# Open Design 项目接入

## 目标

缩小「你想要的」与「Agent 做出来的」之间的差距：

- **ui-spec.md** = 业务信息架构、权限态、中文文案（人读）
- **DESIGN.md** = 视觉 token、组件规则（Agent 读）
- **prototypes/** = 可点击 HTML 预览（你审）

三者一致且 **你签字** 后，frontend 才按原型实现。

## 目录约定

```
docs/design/
  ui-spec.md              # uiux Agent 写
  DESIGN.md               # Open Design 维护
  design-diff.md          # 规格与原型差异
  user-approval.md        # 你签字（Gate 关键）
  prototypes/
    platform/
      home.html
      my-systems.html
    system/
      overview.html
      member-onboard.html
      modeling.html
      runtime-list.html
      runtime-form.html
```

## uiux Agent 调用 Open Design 的时机

仅在 `gates.prd_frozen = true` 且 `gates.design_user_approved = false` 时。

流程：

1. uiux 完成 `ui-spec.md`
2. Conductor 触发 skill `open-design`（或你手动在桌面 OD 操作）
3. 输入 brief 必须引用：
   - `docs/product/prd.md` 中的用户角色
   - 首期剧本：车系统 / che 成员
   - `ui-spec.md` 中的页面清单
4. 输出落盘到 `prototypes/`
5. uiux 写 `design-diff.md`
6. **暂停**，等你 `user-approval.md`

## 推荐设计系统

内部企业业务平台，建议先用偏 **Linear / Notion / Stripe Dashboard** 一类克制风格，避免花哨营销页 skill。

Open Design 内置 150+ `DESIGN.md` 系统，brief 中指定即可。

## 与 frontend 的交接

`user-approval.md` 为 true 后，frontend 任务必须引用：

| 类型 | 路径 |
|------|------|
| 布局 | 对应 `prototypes/**/*.html` |
| 文案 | `ui-spec.md` |
| token | `DESIGN.md` |
| 接口 | `docs/api/api.md`（Design 之后才冻结） |

frontend **不得** 自行改视觉主结构；改动须回到 Design 阶段并重新请你签字。

## 迭代规则

- 小改文案/间距：你可注明在 `user-approval.md` notes，uiux 改 spec 后 OD 重出局部原型
- 改信息架构：pm 参与，可能需更新 prd，重新走 Design Gate
