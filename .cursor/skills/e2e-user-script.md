# Skill: e2e-user-script

## 触发

phase = verify；所有 build 任务 accepted。

## 剧本

车系统 MVP（见 work-graph.md）：

1. platform_admin 登录
2. 创建车系统
3. 开通 che 成员
4. 建模发布车辆模块并授权
5. che 登录 → 运行台 → CRUD
6. 断言：che 无管理/建模菜单

## 输出

- `docs/evidence/e2e-car-system.md`
- 截图/日志附件放 `docs/evidence/e2e-car-system/`

## 规则

- 必须真实浏览器，不得仅 API smoke
- fail → 开 P0 issue，不得打包
