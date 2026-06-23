# Learning: 无代码平台测试 — 租户/SSO/API 需提前进页表

- **date:** 2026-06-23
- **project:** nocode-platform RPFD test
- **trigger:** 对比 examine2 展开差异
- **symptom:** 若仅复用 examine2 defaults，会漏平台租户页、SSO 专页、API Console
- **root_cause:** defaults 面向「可配置系统」通用版，未覆盖「平台+SSO+APIM」组合需求
- **generalized_rule:** L1 扫描需求关键词（SSO/租户/API/对外）→ L2 强制对照 external-references §1–3 增页
- **embedded_to:** external-references.md；待嵌入 defaults.md
- **status:** abstracted
