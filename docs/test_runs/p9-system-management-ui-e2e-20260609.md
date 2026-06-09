# P9 系统管理域 UI E2E 记录

## 环境

| 项目 | 值 |
| --- | --- |
| 日期 | 2026-06-09 |
| 前端 preview | `http://127.0.0.1:4173/` |
| 账号 | `platform_admin / 123123aa` |
| 任务 | TEST-007 |

## 已执行

| 步骤 | 结果 |
| --- | --- |
| `npm.cmd ci` | pass，2 个 moderate audit 保留 |
| `npm.cmd run build` | pass |
| `npm.cmd run preview -- --host 127.0.0.1 --port 4173` | preview HTTP 200 |
| Chrome headless `--dump-dom` | fail，当前 Windows 会话未输出 DOM |
| Chrome headless `--screenshot` | pass，已生成四个 P9 页面截图 |

## 当前结论

`TEST-007` 当前不能判定 pass。当前只完成了浏览器静态 UI smoke，尚未完成真实点击登录、进入系统、成员/部门/角色/字典写操作和权限状态验证。

## UI Smoke 截图

| 页面 | 截图 | 结论 |
| --- | --- | --- |
| 成员 | `frontend/docs/p9-members-smoke.png` | pass，显示邀请成员、成员列表和上下文阻断提示 |
| 部门 | `frontend/docs/p9-departments-smoke.png` | pass，显示创建部门和部门列表 |
| 系统角色 | `frontend/docs/p9-roles-smoke.png` | pass，显示创建角色、加载权限目录和角色列表 |
| 字典 | `frontend/docs/p9-dict-smoke.png` | pass，显示创建类型、创建字典项和字典列表 |

## 已有静态与构建证据

- `frontend/src/App.ts` 已将 `system.members`、`system.departments`、`system.roles`、`system.dict` 分流到专用真实 UI 渲染函数。
- `frontend/src/App.ts` 已为 `DICT-010/011` 删除传 `version` body。
- `frontend/docs/page-contracts/FE-015-system-management-ui.md` 已记录页面/API 映射。
- `docs/build/p9-frontend-clean-build.md` 记录 clean build 通过。

## 待补 E2E

1. 登录 `platform_admin`。
2. 通过 `SYS-001` 进入真实系统。
3. 成员页完成列表、邀请或编辑、启停、分配角色。
4. 部门页完成创建、编辑、删除限制验证。
5. 系统角色页完成创建、权限目录加载、授权保存和有效权限刷新。
6. 字典页完成类型/项创建、启停、usage 查询、version 删除。

## 结论

status: `partial`

target: `test`
