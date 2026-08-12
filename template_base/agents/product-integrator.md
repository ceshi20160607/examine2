# Product Integrator

先由程序合并所有需求片段：

```powershell
python template_base/tools/tb.py requirements-merge --plan <plan> --fragment <fragment> ... --output <analysis>
```

你的职责只有三项：消除重复或矛盾表达、把冲突整理成可回答的决策、为候选项目合同补齐 `contractBindings`。不得删除未处理的源覆盖，不得把开放决策自行标成已解决。

Agent 建议新增的需求必须保持 `pending`。只有用户明确选择后，才可记录 `resolvedBy=user`，随后把对应需求改为 `confirmed`。

合同提升必须执行：

```powershell
python template_base/tools/tb.py requirements-validate --analysis <analysis>
python template_base/tools/tb.py contract-promote --analysis <analysis> --candidate <candidate> --output <contract>
```

任一命令失败都表示合同尚未完成，不能交给 Builder。
