# VS3 真实用户旅程证据

- checked_at: `2026-07-15T11:45:00+08:00`
- verdict: `pass`
- test: `frontend/tests/e2e/vs3.spec.ts`

## 自动浏览器旅程

Playwright 连接真实前端、最新后端 jar、持久化 MySQL `8.4` 和 Redis，使用真实 cookie、CSRF、context 与权限 API：

1. 系统 owner 进入配置工作台，创建模块与字段，配置父字段默认来源、子表列/行策略和嵌套关系过滤。
2. 创建并重开递归规则且保留目标字段，完成两次检查/发布，在前端比较 V1/V2，并从普通运行入口读取 active navigation/definition。
3. 新注册普通账号访问目标系统，进入无成员页面并提交访问申请。
4. 管理员在 UI 中选择租户和角色批准；普通成员重新登录后只看到授权模块，看不到第二模块和系统后台入口，直接请求第二模块 definition 返回 403。
5. 桌面紧凑视口复跑配置、发布和运行旅程并检查资源行边界；移动深链到非首组模块，验证组/模块选择同步、配置只读、检查与版本历史，无页面级横向溢出。
6. 独立自注册系统 Owner 旅程逐一切换 `1199/1200/1277/1278` 临界宽度；1199 为两栏，1200 起为完整三栏，所有宽度均无面板重叠或页面级横向溢出。

| project | viewport | effective journey | result |
|---|---|---|---|
| desktop | `1440x900` + 动态临界宽度 | 配置/发布/运行 + 普通成员申请审批授权 + `1199/1200/1277/1278` 响应式边界 | pass |
| desktop-compact | `1280x720` | 配置/发布/运行 | pass |
| mobile | `390x844` | 配置只读/检查/版本历史 | pass |

## 截图

- `config-studio-desktop.png`
- `config-studio-desktop-compact.png`
- `config-studio-boundary-1200.png`
- `config-studio-mobile.png`
- `ordinary-member-runtime.png`

五张最终截图已人工查看；未发现弹层遮挡、页面级横向溢出、1200/1280 资源行越界或普通成员越权后台入口。
