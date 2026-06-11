# P14 普通用户与 OpenAPI 端到端补证

执行时间：2026-06-11 21:22

执行目标：补齐 reviewer 指出的 P0 缺口，证明普通业务用户不是只读入口，且平台级对外应用可以通过 AK/SK 成功调用系统内模块数据并在日志中追踪。

## 环境

| 项 | 值 |
| --- | --- |
| 后端 | `http://127.0.0.1:18080` |
| 后端包 | `backend/examine-web/target/unexamine.jar` |
| 数据库 | `192.168.0.211:3306/examine1` |
| 管理员 | `platform_admin` |
| 系统 | `2065034340583424001` |
| 租户 | `2065034340587618306` |
| 模块 | `2065034341111906305` / `customer_file_77525602` |

## 后端修复点

| 问题 | 修复 |
| --- | --- |
| 动态模块菜单未同步到系统 RBAC 授权目录，普通成员无法被授予运行台菜单和记录操作。 | 保存运行菜单或动作后同步 `SystemMenu` 和运行台操作：`MENU_VISIBLE`、`RECORD_VIEW`、`RECORD_CREATE`、`RECORD_EDIT`、`RECORD_SUBMIT`、`RECORD_HISTORY_VIEW`。 |
| 角色字段权限保存必须传 `fieldId`，前端按 `moduleId + fieldCode` 授权会 500。 | 保存角色字段权限时根据 `systemId + moduleId + fieldCode` 解析真实字段。 |
| 重复保存模块动作会把多条旧动作更新成同一个 `delete_marker=REPLACED`，撞唯一索引。 | 旧动作和字段选项替换时使用自身 ID 作为删除标记。 |
| OpenAPI 验签通过后复用运行台 service 时，没有用户权限上下文，导致 `COMMON_FORBIDDEN`。 | 新增 OpenAPI 权限快照，将 client scope 映射为运行台操作、字段权限和数据范围。 |
| OpenAPI 日志列表 records 有值但 total 为 0。 | 日志列表补独立 count，分页 total 正确返回。 |

## 普通业务用户链路

| 步骤 | 结果 |
| --- | --- |
| 同步运行菜单 | `menuId=2065057204352929793`，`code=customer_file_77525602` |
| 同步运行操作 | `MENU_VISIBLE`、`RECORD_VIEW`、`RECORD_CREATE`、`RECORD_EDIT`、`RECORD_SUBMIT`、`RECORD_HISTORY_VIEW` |
| 创建普通账号 | `p14_member_212202`，`accountId=2065062001864880129` |
| 创建运行台角色 | `roleId=2065062002313670658` |
| 邀请系统成员 | `memberId=2065062004305965057` |
| 有效菜单 | `customer_file_77525602` |
| 有效操作 | `MENU_VISIBLE`、`RECORD_VIEW`、`RECORD_CREATE`、`RECORD_EDIT`、`RECORD_SUBMIT`、`RECORD_HISTORY_VIEW` |
| 字段权限 | `customer_name`、`phone`、`level` |
| 数据范围 | `MODULE:2065034341111906305:SELF` |
| 访问成员管理 | `403 COMMON_FORBIDDEN` |
| 运行台菜单 | 返回 `customer_file_77525602`，路径 `/systems/2065034340583424001/runtime/modules/2065034341111906305` |
| 新增业务记录 | `recordId=2065062007648825345`，客户名 `普通用户客户212202` |

结论：普通业务用户可以进入被授权业务运行台并新增自己的业务数据，不能访问成员管理等配置入口。

## 平台级对外应用链路

| 步骤 | 结果 |
| --- | --- |
| 创建 OpenAPI 客户端 | `clientId=2065062008223444993` |
| accessKey | `ak_65cQnnlo_QVr4PEhLcn088ZLkpKppPCS` |
| secret | 一次性返回，证据中仅记录 `sk_d***` |
| scope | `record:read`，限定模块 `2065034341111906305`，可读字段 `customer_name`、`phone`、`level` |
| 签名调用 | `POST /openapi/v1/records/query` |
| requestId | `p14-openapi-212202` |
| 调用结果 | `success=true`，返回记录数 `5` |
| 日志查询 | `GET /api/v1/systems/2065034340583424001/openapi/access-logs?requestId=p14-openapi-212202&pageNo=1&pageSize=10` |
| 日志结果 | `total=1`，`statusCode=200`，`signatureResult=PASS`，`scopeResult=PASS`，`rateLimitResult=PASS`，`errorCode=null` |

结论：平台级对外应用不是系统内业务应用菜单。它通过 AK/SK、scope、字段范围和数据范围授权外部系统读取业务模块数据，且调用可按 requestId 在 OpenAPI 日志中追踪。

## 验证命令

| 命令 | 结果 |
| --- | --- |
| `mvn.cmd -pl examine-web -am clean package -DskipTests` | pass |
| `java -jar backend/examine-web/target/unexamine.jar --server.port=18080` | pass |
| PowerShell 端到端脚本 | pass |

## 结论

P14-TEST-001/002/003 的 reviewer P0 补证通过。`P14-PKG-001` 仍等待 reviewer 复审通过后执行。
