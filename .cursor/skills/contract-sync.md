# Skill: contract-sync

## 触发

`api_frozen` 前最后一轮；或 API 变更后。

## 检查

- `docs/api/api.md` 错误码/枚举
- `frontend/src/api/` 类型
- `frontend/docs/api-contract-map.md`（若存在）

## 输出

`docs/evidence/contract-sync-{date}.md` — 不一致项列表，verdict pass/fail

## fail 时

开 issue → owner 为 backend 或 frontend，不得静默改 api
