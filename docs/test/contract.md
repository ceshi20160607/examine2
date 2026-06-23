# 测试契约（MVP）

> P1 · test · 对齐 e2e-car-system.md

## 1. 测试分层

| 层 | 范围 | 工具 |
|----|------|------|
| L1 单元 | backend manage service | JUnit |
| L2 API | 174 端点子集 MVP | Postman/脚本 |
| L3 UI | 车系统剧本 A+B | 浏览器 manual → Playwright |
| L4 部署 | nginx + dist | curl smoke |

## 2. MVP API 必测（按剧本）

| 剧本步 | API |
|--------|-----|
| 登录 | AUTH-002 |
| 进系统 | PLAT-001, SYS-001 |
| 字典 | DICT-* |
| 建模发布 | FIELD, UI, MOD |
| 成员 | MEM-* |
| 角色 | RBAC-* |
| 列表 CRUD | RUN-002~005 |
| 权限负例 | 403 无后台菜单 |

## 3. UI 必测结构点

ui-spec §2.5 九点 — 自动化检查清单（manual 首轮）

## 4. 通过门槛

- L2 MVP 子集 100% pass  
- L3 剧本 A+B 100% pass  
- L4 health + 静态页 200  

## 5. 证据路径

`docs/evidence/` — build-report, e2e-car-system, api-smoke
