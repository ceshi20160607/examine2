# Agent 执行协议

这里的 JSON 和说明不是项目状态的来源。唯一权威是：

```powershell
python template_base/tools/tb.py status --contract <contract> --output <generated>
```

阶段由现有工件、哈希和真实验证证据自动计算，任何 Agent 都无权把阶段手工改成“完成”。

工作采用最少角色和纵向切片：Requirement Analyst 只处理程序分配的一个需求包；Product Integrator 合并原子需求、暴露决策并建立合同绑定；Builder 对一个可独立验收的端到端业务切片负责；Reviewer 只找反例和缺口。基础实体、Mapper、标准 CRUD、页面骨架和 migration 由生成器完成，不分配给 Agent 重写。

需求分析可以按生成的互斥工作包并行，合并由程序检查“每包恰好一次”。实现只有合同稳定后才能并行；并行单位是互不改动共享边界的业务纵切，不是把同一个功能机械拆成“前端完成、后端完成”。一个切片没有通过真实 UI → API → MySQL 旅程，就不能被记为工程完成。

`user-acceptance.json` 只能来自用户明确验收。Agent 不得创建、代填或把测试结果解释成用户验收。

具体角色输入输出见 [requirement-analyst.md](requirement-analyst.md) 和 [product-integrator.md](product-integrator.md)。
