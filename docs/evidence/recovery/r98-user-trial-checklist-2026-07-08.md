# R98 用户试用与签字清单

生成时间：2026-07-08T17:04:49.1569648+08:00
当前地址：http://127.0.0.1:18131
前端资源：/assets/index-CejcRNev.js, /assets/index-BKptqjBa.css

## 先看边界

- 这份清单是工程就绪证据，不是最终用户验收。
- 当前 `.cursor/session/state.json` 仍保持 `gates.user_script_passed=false`。
- 覆盖审计仍有未关闭行：`45`。只有你明确说“试用通过/可以签字”后，后续任务才允许把用户签字 gate 改为 true。
- R98 浏览器烟测状态：`PASS`，路线数：`11`。

## 试用账号

| 角色 | 账号 | 密码 | 建议入口 |
|---|---|---|---|
| 平台管理员 | `admin` | `123123aa` | `http://127.0.0.1:18131/#/platform` |
| 运行态普通成员 | `r63_member_0706162707_4a00b6` | `Aa123456!` | `http://127.0.0.1:18131/#/systems/1118/modules` |
| 运行态只读成员 | `r63_readonly_0706162707_4a00b6` | `Aa123456!` | `http://127.0.0.1:18131/#/systems/1118/modules` |
| 流程申请人 | `r3_requester_0706162803307_ca6efa` | `Aa123456!` | `http://127.0.0.1:18131/#/systems/1121/modules` |
| 流程审批人 | `r3_approver_0706162803307_ca6efa` | `Aa123456!` | `http://127.0.0.1:18131/#/systems/1121/todos` 与 `http://127.0.0.1:18131/#/systems/1121/messages` |

## 建议人工试用顺序

1. 用平台管理员登录，打开 `/platform`，确认这是“系统入口工作台”，可以搜索并进入系统。
2. 同一账号打开 `/platform/apps` 和 `/platform/flow`，确认 Application/Flow 是独立平台模块，不再混成系统入口。
3. 用平台管理员打开 R97 新系统 `1177` 的 `/systems/1177/dashboard`，确认空系统只提示初始化；再打开 `/systems/1177/admin`，确认 C1 十步初始化清单存在。
4. 用运行态普通成员打开系统 `1118` 的 `/systems/1118/modules`，确认能看到业务运行态页面和记录数据。
5. 用运行态只读成员打开同一路线，确认可看但不能新增/编辑越权数据。
6. 用流程申请人打开系统 `1121` 的运行态模块，确认能看到流程终态记录 `626` 相关页面。
7. 用流程审批人打开待办和消息中心，确认待办/消息路线可进入，历史流程处理结果能被理解。

## 如何反馈

- 如果所有步骤符合预期，请明确回复：`R98 用户试用通过，可以进入最终签字处理`。
- 如果有问题，请给出账号、入口、页面现象和你期望看到的样子。下一批必须从这个问题建 recovery task card，再编码。

## 机器证据

- R97 结果：`docs/evidence/recovery/r97-c1-fresh-system-initialization-path-result.json`
- R98 结果：`docs/evidence/recovery/r98-final-user-trial-readiness-and-signoff-path-result.json`
- R98 浏览器烟测：`docs/evidence/recovery/screenshots/r98-final-user-trial-readiness-and-signoff-path/final-user-trial-readiness-browser-audit.json`
- R98 截图/日志目录：`docs/evidence/recovery/screenshots/r98-final-user-trial-readiness-and-signoff-path/`
