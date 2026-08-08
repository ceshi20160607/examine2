# VS2 真实用户旅程证据

- checked_at: `2026-07-11T10:39:26+08:00`
- verdict: `pass`
- tests: `frontend/tests/e2e/vs1.spec.ts`, `frontend/tests/e2e/vs2.spec.ts`

## 桌面 Root 旅程

Playwright 使用本机 Edge 和真实 backend/MySQL/Redis：

1. Root 登录并进入平台后台。
2. 打开组织账号、平台角色，确认允许/拒绝双列。
3. 选择真实账号查看有效权限来源角色、数据范围和 ALLOW/DENY 决策。
4. 通过系统治理弹窗创建系统，读取真实 POST 响应和系统 ID，并在列表确认持久化结果。
5. 返回工作台进入新系统，再进入系统后台。
6. 依次进入系统设置、租户管理、组织成员、角色权限和访问申请，确认真实路由和页面可达。

## 移动边界旅程

1. 在 `390x844` 注册新账号和系统并进入系统工作台。
2. 切回平台、再次进入系统，验证 context 切换。
3. 管理页面明确显示移动端只读提示，不暴露复杂 mutation 控件。
4. 每个关键页面断言 `document/body scrollWidth <= viewport + 1`，无横向溢出。

## 累积回归

| project | viewport | VS1 | VS2 | result |
|---|---|---|---|---|
| desktop | `1440x900` | 注册/系统壳 | Root 治理旅程 | pass |
| mobile | `390x844` | 注册/系统壳 | context/只读边界 | pass |

组合命令实际执行 `4` 条旅程全部通过；VS2 的 desktop-only 测试在 mobile project、mobile-only 测试在 desktop project 各按设计跳过一次。根账号凭据只从环境读取且关闭 trace，不写入截图、日志或证据。

Codex 内置浏览器另在 `1280x720` 检查登录页和平台角色页：DOM 结构完整，允许/拒绝双列可读，`body/doc` 宽度 `1265 <= 1280`，无空白、遮挡或水平溢出。
