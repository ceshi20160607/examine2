# 后端契约 gap（草案）

> P1 · backend · 对照新 IA 与现有 Controller

## 1. 模块组导航

| API | 现状 | 需增/改 |
|-----|------|---------|
| SYS-001 进入系统 | 返回 menus | menus 需 **group→modules** 树 |
| MOD 模块列表 | 可能有 app 层级 | 文档明确「组下模块」过滤 |
| UI 菜单发布 | 发布 snapshot | snapshot 含 moduleGroupId |

## 2. 运行态列表

| 能力 | API | MVP |
|------|-----|-----|
| 场景列表 | RUN listViews? | 若无则 MOD 配置读 snapshot |
| 列设置保存 | member pref API | 可新增 RUN-011 或 MEM 扩展 |
| 导出 | RUN export | 已有则复用 |
| 右侧详情+审批 | RUN detail + FLOW instance | 合并 VO 或前端双请求 |

## 3. 配置态

| 页 | API 组 | 备注 |
|----|--------|------|
| 建模六 Tab | FIELD/UI/MOD | 基本已有 |
| 角色四 Tab | RBAC-005~013 | 基本已有 |
| 仪表盘三栏 | 可能缺 designer API | MVP 可 JSON 配置 stub |
| 对外映射 | OPM | MVP 单行映射 |

## 4. 建议冻结策略

- **不删**现有 174 端点
- **扩展** SYS-001 menu 结构 + 可选 2~3 新端点
- 新端点在 `api.md` 显式标注 ADD
