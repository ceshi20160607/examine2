# VAL-003 契约同步检查记录

- 任务: VAL-003
- 执行时间: 2026-06-08 01:52
- 执行角色: validator
- 结论: pass
- target: none

## 输入

| 输入 | 结果 |
| --- | --- |
| `docs/api.md` | 存在，冻结 API 契约 |
| `frontend/src/api/endpoints.ts` | 存在 |
| `frontend/src/api/enums.ts` | 存在 |
| `frontend/src/api/errorCodes.ts` | 存在 |
| `frontend/docs/api-contract-map.md` | 存在 |

## 检查方法

使用 Node 临时脚本从冻结 API 和前端契约文件中抽取 API ID、核心错误码、字段类型枚举、状态枚举和 AUTH 鉴权标记，并做集合差异比对。脚本过滤 `FE-*` 页面编号和 `SHA-256` 算法文本，避免将非 API 编号误计为接口。

## 检查结果

| 检查项 | API 数量 | 前端数量 | 结果 |
| --- | ---: | ---: | --- |
| API ID | 174 | 174 | pass |
| `api-contract-map.md` API ID | 174 | 174 | pass |
| 核心错误码 | 20 | 139 | pass，核心错误码均已包含，前端额外包含逐模块错误码 |
| 状态枚举 | 14 组 | 14 组 | pass |
| 字段类型枚举 | 19 | 19 | pass |
| AUTH-004/AUTH-005 鉴权 | 2 | 2 | pass，SDK 与契约映射均为 `Bearer` |

## 字段类型枚举

冻结 API 字段类型 MVP 枚举与前端 `DYNAMIC_FIELD_TYPES` 均为：

```text
TEXT, TEXTAREA, NUMBER, MONEY, DATE, DATETIME, SELECT, MULTI_SELECT, SWITCH,
MEMBER, DEPT, ATTACHMENT, IMAGE, AUTO_NO, RELATION, SUB_TABLE, ADDRESS, TAG, JSON
```

| 类型 | 值 |
| --- | --- |
| API 有、前端缺失 | 无 |
| 前端有、API 未冻结 | 无 |

## 结论

VAL-003 返工复验通过。前端 SDK、字段类型、状态枚举、核心错误码和 `frontend/docs/api-contract-map.md` 已同步冻结版 `docs/api.md`。
