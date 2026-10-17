# 当前工程执行入口

唯一产品需求源是 `docs/user_requirement.md`。`spark-execution-plan.md` 开头记录 2026-10-09 的全项目取舍及实施顺序；`spark-state.json` 记录当前准确步骤，`spark-requirement-ledger.json` 仅用于原文索引和验收追溯。以当前需求和实际代码、页面结果判断可用性，不从旧周期或旧完成状态继承结论。

每次实施前运行：

```powershell
python template_base/tools/tb.py workspace-validate --workspace workspace.json
python template_base/tools/tb.py execution-context --workspace workspace.json
```

按不超过预计 240 分钟的业务结果组织任务包。表结构变动先空库验证，再运行 Base 生成器，最后适配 `manage` 和页面。实际验收记录写入后运行 `execution-verify --workspace workspace.json --receipt <当前回执>`；该工具只验证绑定和证据完整性，不能证明业务正确。完成整包组合验证后更新准确断点，继续下一步骤。

旧需求分析、旧复评、任务图和排期文件已从项目执行资料中移除。当前已确认规则以唯一需求源为准；本目录保留产品基线摘要、当前方案、需求行索引、执行控制和准确断点。测试证据只描述其覆盖的当前结构与业务场景。
