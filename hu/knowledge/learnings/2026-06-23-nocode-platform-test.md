# Learning: 无代码平台测试 — 租户/SSO/API 需提前进页表

- **date:** 2026-06-23
- **project:** nocode-platform RPFD test
- **trigger:** 对比 examine2 展开差异
- **symptom:** 若仅复用默认页表，会漏平台租户页、SSO 专页、API Console
- **root_cause:** defaults 面向通用可配置系统，未覆盖平台+SSO+APIM 组合
- **generalized_rule:** L1 扫描 SSO/租户/API/对外 → L2 对照 external-references 增页
- **embedded_to:** external-references.md；defaults.md 关键词节；lessons F1
- **status:** embedded
