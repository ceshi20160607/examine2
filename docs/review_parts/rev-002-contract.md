# REV-002 契约实现审查

- 任务: REV-002
- 执行时间: 2026-06-08
- 负责角色: reviewer
- 结论: fail
- target: both

## Findings

### P1 AUTH-004/AUTH-005 前端 SDK 鉴权标记与冻结 API 和后端实现不一致

- file: `frontend/src/api/endpoints.ts:63`
- evidence: `docs/api.md:596` 和 `docs/api.md:597` 明确 `AUTH-004 logout`、`AUTH-005 me` 鉴权为 Bearer；后端 `AuthController.logout` 与 `AuthController.me` 均要求 `@RequestHeader(HttpHeaders.AUTHORIZATION)`。前端 SDK 和 `frontend/docs/api-contract-map.md` 却把两个接口标为 `None`。
- impact: 页面模型或调用方可能不附带 Bearer token，导致退出和当前用户查询在真实运行时返回 401；契约映射也会误导测试和页面权限判断。
- target: frontend
- recommendation: 将 `AUTH-004`、`AUTH-005` 的前端 endpoint auth 从 `None` 改为 `Bearer`，并同步更新 `frontend/docs/api-contract-map.md`。

### P1 OpenAPI accessKey 缺失/非法错误码与冻结契约不一致

- file: `backend/examine-app/src/main/java/com/unique/examine/app/manage/service/impl/OpenApiSecurityServiceImpl.java:91`
- evidence: `docs/api.md:291`、`docs/api.md:506` 和 `docs/api.md:986` 冻结为 `OPENAPI_ACCESS_KEY_INVALID`；当前后端缺少 `X-OpenApi-AccessKey` 时使用 `CommonErrorCode.UNAUTHORIZED`，`OpenApiErrorCode` 中也没有 `OPENAPI_ACCESS_KEY_INVALID`，而是 `OPENAPI_CLIENT_NOT_FOUND`。
- impact: OpenAPI 安全负向用例无法按冻结 API 断言模块化错误码；前端/外部调用方和测试会看到通用未授权或未冻结的 OpenAPI 错误码，契约不一致。
- target: backend
- recommendation: 后端补充或调整 OpenAPI accessKey 错误码为 `OPENAPI_ACCESS_KEY_INVALID`，缺失、格式非法和查不到客户端时都按冻结契约返回；若产品希望使用 `OPENAPI_CLIENT_NOT_FOUND`，需重开 API 契约评审。

### P1 字段类型枚举仍未按冻结 API 同步到前端

- file: `frontend/src/api/enums.ts:22`
- evidence: `docs/api.md:127` 冻结字段类型包含 `MONEY`、`SWITCH`、`MEMBER`、`DEPT`、`AUTO_NO`；前端 `DYNAMIC_FIELD_TYPES` 和 `frontend/src/api/types.ts` 使用未冻结的 `DECIMAL`、`RADIO`、`CHECKBOX`、`DICT`、`BOOLEAN`、`SERIAL`，并缺少冻结值。
- impact: 字段配置、运行态 schema、表单渲染和字段校验会产生前后端语义漂移。
- target: frontend
- recommendation: 前端类型和枚举按冻结 API 同步，或回到 API 契约评审确认是否整体改名。

## Pass Items

| 检查项 | 结论 |
| --- | --- |
| API ID 覆盖 | VAL-003 已确认 174 个冻结 API ID 均同步到 `frontend/src/api/endpoints.ts` 和 `frontend/docs/api-contract-map.md`。 |
| 核心错误码 | VAL-003 已确认 20 个核心错误码均包含于前端错误码集合。 |
| 状态枚举 | VAL-003 已确认 15 组状态枚举同步。 |
| typed SDK 调用入口 | FE-012 记录 `frontend/src` 未发现直接 `fetch`、`axios`、`XMLHttpRequest`、`new Request` 或硬编码 URL。 |

## Review Conclusion

契约实现审查不通过，target=both。前端需要修正 AUTH 鉴权标记和字段类型枚举；后端需要修正 OpenAPI accessKey 错误码实现。修复后应重跑 VAL-003，并由 TEST-004 补 OpenAPI accessKey 负向断言。
