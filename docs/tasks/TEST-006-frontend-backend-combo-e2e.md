# TEST-006 前后端组合 E2E 与部署验收

- 任务 ID: TEST-006
- 所属期次: P7-frontend-ui-deploy
- 负责角色: test
- 状态: pending
- 优先级: P0

## 目标

在后端 jar 和前端 `frontend/dist/` 均已生成后，启动组合环境，验证用户能通过浏览器完成核心系统链路。

## 输入文件

- `backend/examine-web/target/unexamine.jar`
- `frontend/dist/`
- `docs/api.md`
- `docs/test_report.md`
- `frontend/docs/frontend-ui-smoke.md`
- `docs/service_info.md`

## 输出文件或目录

- `docs/test_runs/frontend-backend-combo-e2e.md`
- 更新 `docs/test_report.md`
- 更新 `docs/review.json`

## 完成标准

1. 后端服务使用当前数据库配置启动成功。
2. 前端生产产物可通过静态服务或部署容器访问。
3. 浏览器完成登录、我的系统、进入系统、模块配置/运行台查看。
4. 至少触发一个真实后端 API，并记录请求成功或明确业务失败响应。
5. 覆盖一个错误态和一个权限禁用态。
6. 若失败，明确 target 为 backend、frontend、test、validator 或 pm。

## 验证命令

实际命令由 test 根据本机端口和部署方式记录到 `docs/test_runs/frontend-backend-combo-e2e.md`。
