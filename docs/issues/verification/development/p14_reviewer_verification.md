# P14 reviewer 复审记录

复审时间：2026-06-11 21:30

结论：pass。

## 关闭问题

| issue | 级别 | 结论 | 证据 |
| --- | --- | --- | --- |
| 普通业务用户缺真实登录、菜单、模块、字段和数据范围隔离证据 | P0 | closed | `docs/test_runs/p14-ordinary-user-openapi-e2e-20260611.md` |
| 平台级对外应用缺 AK/SK 外部调用成功和 requestId 日志证据 | P0 | closed | `docs/test_runs/p14-ordinary-user-openapi-e2e-20260611.md` |
| `docs/test_report.md` 仍旧，缺 P14 汇总 | P1 | closed | `docs/test_report.md` |

## 复核摘要

- 普通业务用户 `p14_member_212202` 具备授权运行菜单、运行操作、字段权限和 `SELF` 数据范围，可新增业务记录 `2065062007648825345`。
- 同一普通业务用户访问成员管理返回 `403 COMMON_FORBIDDEN`。
- OpenAPI client `2065062008223444993` 使用 AK/SK 调用 `POST /openapi/v1/records/query` 成功，`requestId=p14-openapi-212202`。
- OpenAPI 日志按 requestId 查询返回 `total=1`、`statusCode=200`、`signatureResult=PASS`、`scopeResult=PASS`、`rateLimitResult=PASS`。
- `docs/test_report.md` 已置顶 P14 测试报告。
- `mvn.cmd -pl examine-web -am clean package -DskipTests` 通过；`git diff --check` 仅 LF/CRLF warning。

## 打包结论

允许进入 `P14-PKG-001`。

打包要求：

- 纳入新增 `backend/examine-app/src/main/java/com/unique/examine/app/manage/permission/OpenApiPermissionSnapshotProvider.java`。
- 不纳入 `.codex/tmp/` 临时日志。
- 包含前端 dist、后端 jar、`start.sh`、nginx 部署文档和 P14 build/test/review 证据。
