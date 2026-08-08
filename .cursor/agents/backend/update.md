# examine2 Backend 增量规约

- 只为 `state.json` 中 Gate 已接受的当前任务编码；不从本文件推断 coding 开关。
- 每个切片先冻结其数据、接口、权限、状态、错误、生成/手写和测试契约。
- 依据 schema 生成 base 基础代码，再在业务层手写真实行为；生成 CRUD、API 200 和单测通过都不是用户功能完成。
- 产品或 UI 行为有疑问时提交 PM，由提交人 + product + uiux 及相关角色会审。
