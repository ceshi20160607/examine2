# P12 FE-024 返工决策

- 时间：2026-06-10
- 主持角色：pm/orchestrator
- 触发来源：REV-008 预审
- 结论：FE-024 第一轮虽然 build 通过，但 reviewer 指出仍存在可用性缺口，PM 决定暂停 TEST-010/VAL-008，先返工 FE-024。

## 预审问题

| 问题 | PM 决策 | 处理状态 |
| --- | --- | --- |
| 我的系统缺少显式进入按钮 | 必须按页面级原型调整 | resolved |
| 平台账号、平台角色仍使用 `window.prompt` | 必须改为页面内表单 | resolved |
| 系统角色授权仍是编码输入 | 必须改为权限目录多选 | resolved |
| 运行台、流程、导出 Tabs 为静态外观 | 必须具备真实切换状态 | resolved |
| TEST-010/VAL-008 未完成 | 不允许 REV-008 通过，不允许打包 | open |

## 返工结果

- `frontend/src/App.ts` 已移除 `window.prompt`。
- 平台账号/角色编辑、重置密码、角色分配和角色授权已改为页面内表单。
- 系统角色权限授权已改为操作权限/菜单权限多选。
- 运行台详情、流程工作台和导出页 Tabs 已改为有状态切换。
- `npm.cmd run build` 已通过。

## 下一步

1. 恢复 TEST-010，执行 P12 UI 可用性 E2E。
2. TEST-010 通过后执行 VAL-008 clean build/后端 package，但仍不得生成最终部署包。
3. VAL-008 通过后执行 REV-008，审查通过才允许 PKG-001。
