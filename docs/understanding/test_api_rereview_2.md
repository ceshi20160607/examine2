# test API 契约第 2 次闭环窄范围复核

## 复核结论：pass

本次仅复核 `TAPI-005`、`TAPI-006` 两个 test 剩余 issue，依据 `docs/api.md`、`docs/api_review.md`、`docs/understanding/test_api_rereview.md`、`docs/service_info.md` 和 `.codex/state.json`。未读取旧项目目录，未运行测试，未修改 `docs/api.md`，未写代码、SQL、后端或前端目录。

## TAPI-005 复核：closed

依据：

- 上轮仍 open 的原因是 OpenAPI 签名缺少签名输出编码、`CANONICAL_QUERY` 排序/URL 编码/重复参数规则、空 query/body/hash 规则、body hash 输入口径和可复现样例。
- `docs/api.md` 已明确 OpenAPI 签名算法固定为 `HMAC-SHA256`，`X-OpenApi-Signature` 固定为 HMAC 输出的小写 hex 字符串，不使用 base64。
- `docs/api.md` 已定义 `canonicalRequest`、`stringToSign`、`X-OpenApi-Signature = LOWER_HEX(HMAC_SHA256(secret, UTF8(stringToSign)))`。
- `docs/api.md` 已定义 `CANONICAL_QUERY`：空 query 为零长度字符串；非空 query 按 UTF-8 RFC 3986 percent-encode 后，以 `name`、`value` 升序排序；重复参数保留为多组 pair；空值写为 `name=`。
- `docs/api.md` 已定义 `BODY_SHA256_HEX`：对 HTTP 请求 body 原始字节计算 SHA-256 小写 hex；无 body 使用空字节 hash；JSON 按实际发送字节计算，不重新格式化为 canonical JSON。
- `docs/api.md` 已定义签名 header 规范化、固定 `SIGNED_HEADERS`、`X-OpenApi-Body-Sha256` 校验、timestamp 13 位 Unix milliseconds、正负 300 秒窗口、nonce 唯一键和 10 分钟 TTL。
- `docs/api.md` 已定义限流维度、策略字段、HTTP 429、`OPENAPI_RATE_LIMITED`、`Retry-After`、`X-RateLimit-*` headers，以及 `meta.rateLimit.limit/remaining/resetAt/retryAfterSeconds/dimension` 且 `remaining=0`。
- `docs/api.md` 已提供可复现测试样例，包含 method、path、query、body bytes、headers、secret、canonicalRequest、canonicalRequestSha256 和 expectedSignature，并列出空 query、重复 query、空 body、JSON body 原始字节变化、header 大小写、timestamp、nonce、body hash、签名、scope、限流等自动化覆盖点。

test 角度判断：OpenAPI 安全用例已可稳定构造签名并断言限流响应，`TAPI-005` 关闭。

## TAPI-006 复核：closed

依据：

- 上轮仍 open 的原因是字典接口缺少请求/响应字段、层级/级联字段、权限点、状态影响、引用限制、缓存刷新和错误码。
- `docs/api.md` 已补 `DICT-001` 至 `DICT-011`，覆盖字典类型列表、创建、更新、启停、字典项列表、创建、更新、启停、使用情况、删除字典类型、删除字典项。
- `docs/api.md` 已为每个 DICT 接口列出鉴权、权限点、前置条件和状态影响，包括 `DICT_VIEW`、`DICT_CREATE`、`DICT_EDIT`、`DICT_STATUS`、`DICT_ITEM_CREATE`、`DICT_ITEM_EDIT`、`DICT_ITEM_STATUS`、`DICT_DELETE`、`DICT_ITEM_DELETE`。
- `docs/api.md` 已定义 `DictTypeQuery`、`DictTypeSaveBO`、`DictTypeUpdateBO`、`DictStatusBO`、`DictItemQuery`、`DictItemSaveBO`、`DictItemUpdateBO`、`DictDeleteBO` 的字段、必填、前端可写和规则。
- `docs/api.md` 已定义 `DictTypeVO`、`DictItemVO`、`DictUsageVO`、`DictCacheRefreshVO`，其中字典项包含 `parentId`、`depthLevel`、`depthPath`、`leaf`、`children` 等层级断言字段。
- `docs/api.md` 已定义字典唯一性、状态、排序、系统/租户范围、最大 5 级层级、父级不存在、父级停用、停用/删除引用限制、历史展示快照、新写入只允许 `ENABLED` 字典项等规则。
- `docs/api.md` 已定义缓存键、`cacheVersion` 递增、写接口返回 `DictCacheRefreshVO`、缓存刷新失败返回 `DICT_CACHE_REFRESH_FAILED` 且 `retryable=true`。
- `docs/api.md` 已定义字典错误码，包括 `DICT_TYPE_NOT_FOUND`、`DICT_ITEM_NOT_FOUND`、`DICT_TYPE_CODE_DUPLICATE`、`DICT_ITEM_CODE_DUPLICATE`、`DICT_ITEM_VALUE_DUPLICATE`、`DICT_PARENT_NOT_FOUND`、`DICT_PARENT_DISABLED`、`DICT_DEPTH_EXCEEDED`、`DICT_TYPE_IN_USE`、`DICT_ITEM_IN_USE`、`DICT_HAS_ENABLED_CHILDREN`、`DICT_BUILTIN_READONLY`、`DICT_SCOPE_INVALID`、`DICT_STATUS_CONFLICT`、`DICT_CACHE_REFRESH_FAILED`。
- `docs/api.md` 已给出字典测试断言：类型编码重复、同父级 item code/value 重复、6 级字典项超限、引用阻塞和写后 `cacheVersion` 增加。

test 角度判断：字典配置数据、引用限制、状态规则、层级规则和缓存刷新均已有可测试字段与断言，`TAPI-006` 关闭。

## 剩余阻塞 issue

test 角度无剩余阻塞 issue。

本次不扩展无关新问题，也不判断非 test 范围的其它角色待复核项。

## 是否允许 API 冻结/进入任务拆分：test 角度结论

test 角度允许 API 冻结，并允许进入任务拆分前置条件。最终是否冻结仍应由 PM 汇总所有角色复核结论后决定。
