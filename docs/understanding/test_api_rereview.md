# test API 契约第 1 次闭环修订复核

## 复核结论：fail

第 1 轮 API 契约闭环修订后，`TAPI-001`、`TAPI-002`、`TAPI-003`、`TAPI-004`、`TAPI-007`、`TAPI-008` 已达到 test 复核关闭条件；`TAPI-005`、`TAPI-006` 仍未关闭。当前不建议从 test 角度冻结 API，也不建议进入任务拆分。

主要阻塞原因：

- `TAPI-005`：OpenAPI 限流规则已可断言，但签名规范仍缺少签名输出编码、`CANONICAL_QUERY` 生成规则、空 body/hash 规则等关键细节，自动化测试无法稳定构造与后端一致的签名。
- `TAPI-006`：字典接口已补 API ID 和路径，但仍缺少字典类型、字典项、层级/级联字段的请求/响应字段、权限点、状态影响和错误码，测试无法稳定准备字典配置数据。

## 复核范围

本次只基于以下文件复核：

- `docs/user_requirement.md`
- `docs/prd.md`
- `docs/project_understanding.md`
- `docs/api.md`
- `docs/api_review.md`
- `docs/understanding/test_api_review.md`
- `docs/service_info.md`
- `.codex/state.json`

本次未读取旧项目目录，未运行测试，未修改 `docs/api.md`，未写代码、SQL、前端或后端目录。

## issue 关闭复核表

| issueId | 原问题 | 复核结论 | 依据 | 如仍 open 的修改建议 |
| --- | --- | --- | --- | --- |
| TAPI-001 | 错误码缺 HTTP 状态、触发条件、重试、`errors[]`、字段定位。 | closed | `docs/api.md` 已补 `ApiErrorResponse`、`errors[]` 明细、`fieldCode`/`reason`/`retryable`/`userMessage` 等可断言字段，并在“状态流转与错误码断言”中补核心错误码的 `httpStatus`、`trigger`、`retryable`。 | 无。后续实现阶段按“模块级错误码必须维护 userMessage/auditRequired”的结构继续细化即可。 |
| TAPI-002 | 平台初始化和最小测试数据边界不足。 | closed | `docs/api.md` 已明确生产 seed 仅包含平台默认管理员、平台角色、平台菜单、平台配置和字段类型元数据；默认管理员为 `platform_admin`，绑定 `PLAT_SUPER_ADMIN`；`PLAT-002` 创建系统需事务内初始化默认租户 `default`、创建人成员、`SYS_SUPER_ADMIN`、默认菜单/权限、默认应用 `default_app`，并返回 `initializedObjects` 供断言。 | 无。 |
| TAPI-003 | 状态流转表不足。 | closed | `docs/api.md` 已补账号、系统、租户、应用、模块、字段、记录、流程实例、流程任务、文件、导出任务、OpenAPI 客户端的允许流转和禁止操作错误码。状态冲突用例已有期望错误码。 | 无。 |
| TAPI-004 | 幂等语义不完整。 | closed | `docs/api.md` 已补必须幂等接口清单、内部 API/OpenAPI scope、`requestHash`、结果快照、TTL、同 key 同 hash 回放、同 key 不同 hash 冲突、处理中 423 + `Retry-After` 和日志落点。 | 无。 |
| TAPI-005 | OpenAPI 签名和限流细则不足。 | still_open | `docs/api.md` 已补 HMAC-SHA256、规范化字符串字段、Unix milliseconds、300 秒窗口、nonce 10 分钟、限流维度、HTTP 429、`Retry-After` 和 `rateLimit.remaining=0`。但仍未定义 `X-OpenApi-Signature` 的输出编码（hex/base64、大小写）、`CANONICAL_QUERY` 的参数排序/URL 编码/重复参数规则、无 query 和空 body 的 hash 规则、请求 body 是按原始字节还是 canonical JSON 计算。 | PM/API 需在 `docs/api.md` 的 OpenAPI 签名小节补齐：签名公式、签名输出编码、query 规范化算法、body hash 输入口径、空 query/body 示例，以及至少 1 个可复现 canonical string 示例。 |
| TAPI-006 | 字典接口缺失。 | still_open | `docs/api.md` 已补 `DICT-001` 至 `DICT-009` 路径，覆盖字典类型、字典项、启停和使用情况入口。但当前仅有 API ID/路径/一句说明，未给出 `DictTypeSaveBO`、`DictItemSaveBO`、`DictTypeVO`、`DictItemVO` 字段，未说明层级字典的 `parentId`、`depthLevel`、`depthPath` 或等价字段，未列权限点、状态影响和字典错误码。 | PM/API 需补齐 DICT 接口组完整契约：请求字段、响应字段、层级/级联字典字段、启停规则、使用情况返回结构、权限点、错误码（如编码重复、存在引用不可停用/删除、父级不存在、层级超限）。 |
| TAPI-007 | 审计字段不足，无法断言业务变更、幂等、OpenAPI 安全结果。 | closed | `docs/api.md` 已补 `AuditLogDetailVO` 的 `source`、`apiId`、`httpMethod`、`path`、`operator*`、`bizType`、`bizId`、`beforeStatus`、`afterStatus`、`changedFields`、`beforeSnapshot`、`afterSnapshot`、`errorCode`、`durationMs`；已补 `OpenApiAccessLogVO` 的 `signatureResult`、`nonceResult`、`idempotencyResult`、`rateLimitResult`、`scopeResult`，并补 `AUD-007/AUD-008` 详情接口。 | 无。 |
| TAPI-008 | 流程工作台缺我的申请、抄送、实例/历史入口。 | closed | `docs/api.md` 已补 `FLOW-013` 抄送列表、`FLOW-014` 我的申请、`FLOW-017` 实例列表、`FLOW-018` 审批历史列表，并补领取/取消领取、模板详情和流程图接口；申请人、抄送、实例历史用例可覆盖。 | 无。 |

## 剩余阻塞 issue

| issueId | 第几轮 | 未关闭原因 | 需要 PM/API 修改 |
| --- | --- | --- | --- |
| TAPI-005 | 第 1 轮 | OpenAPI 签名契约仍无法让测试稳定生成与后端一致的签名。 | 补齐签名输出编码、query 规范化、body hash 口径和可复现示例。 |
| TAPI-006 | 第 1 轮 | 字典接口只有入口，没有字段、层级、权限、状态和错误码契约。 | 补齐 DICT 接口组请求/响应/错误码/层级规则。 |

## test 角度结论

不允许 API 冻结。

不允许进入任务拆分。

待 PM/API 修订 `TAPI-005`、`TAPI-006` 后，test 需再次复核。若仅补齐上述两项，不需要重新扩展新的 test issue。
