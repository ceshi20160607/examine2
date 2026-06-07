# VAL-003 契约同步检查记录

- 任务: VAL-003
- 执行时间: 2026-06-08 00:58
- 执行角色: validator
- 结论: fail
- target: frontend

## 输入

| 输入 | 结果 |
| --- | --- |
| `docs/api.md` | 存在，冻结 API 契约 |
| `frontend/src/api/endpoints.ts` | 存在 |
| `frontend/src/api/enums.ts` | 存在 |
| `frontend/src/api/errorCodes.ts` | 存在 |
| `frontend/docs/api-contract-map.md` | 存在 |

## 检查方法

使用 Node 脚本从冻结 API 和前端契约文件中抽取 API ID、核心错误码、字段类型枚举和状态枚举，并做集合差异比对。由于当前前端没有 `package.json`，本任务不执行 TypeScript 编译，只做源码与文档级契约核对。

## 检查结果

| 检查项 | API 数量 | 前端数量 | 结果 |
| --- | ---: | ---: | --- |
| API ID | 174 | 174 | pass |
| `api-contract-map.md` API ID | 174 | 174 | pass |
| 核心错误码 | 20 | 125 | pass，核心错误码均已包含，前端额外包含逐模块错误码 |
| 状态枚举 | 15 组 | 15 组 | pass |
| 字段类型枚举 | 19 | 20 | fail |

## 字段类型差异

冻结 API 字段类型 MVP 枚举：

```text
TEXT, TEXTAREA, NUMBER, MONEY, DATE, DATETIME, SELECT, MULTI_SELECT, SWITCH,
MEMBER, DEPT, ATTACHMENT, IMAGE, AUTO_NO, RELATION, SUB_TABLE, ADDRESS, TAG, JSON
```

前端 `DYNAMIC_FIELD_TYPES`：

```text
TEXT, TEXTAREA, NUMBER, DECIMAL, DATE, DATETIME, SELECT, MULTI_SELECT, RADIO,
CHECKBOX, DICT, BOOLEAN, ATTACHMENT, IMAGE, SERIAL, RELATION, SUB_TABLE, ADDRESS, TAG, JSON
```

| 类型 | 值 |
| --- | --- |
| API 有、前端缺失 | `AUTO_NO`、`DEPT`、`MEMBER`、`MONEY`、`SWITCH` |
| 前端有、API 未冻结 | `BOOLEAN`、`CHECKBOX`、`DECIMAL`、`DICT`、`RADIO`、`SERIAL` |

## 结论

VAL-003 已执行，结论为 fail，target=frontend。前端 SDK 的字段类型枚举未按冻结 API 同步，会影响动态字段配置、运行态表单和页面契约判断；后续需要按 `docs/api.md` 修正 `frontend/src/api/enums.ts` 和相关类型引用，或由 PM/API 正式重开契约确认枚举改名。
