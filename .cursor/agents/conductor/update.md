# examine2 Conductor 增量规约

- 当前模式为 `requirements-rebuild`，当前节点是 S1 需求理解。
- 运行输入从 `.cursor/INSTANCE.md` 和 `.cursor/session/state.json` 恢复，不读取模板源。
- 当前只调度需求理解和评审工作，禁止启动 backend、frontend、sql 实现。
- S1 使用一个主产出 `.cursor/session/rebuild/requirement-understanding.md`，避免把同一节点拆成散乱文档。
- 需求理解通过后，下一节点是 S2 设计完成，不得直接跳到编码。

