# design-diff — FlexBase RPFD 测试

> 对照 checklist + design-package 33 P0

## 汇总

| 项 | 结果 |
|----|------|
| L0-L3 文档 | ✅ 完成 |
| P0 规划 | 33/33 ✅ |
| P0 HTML | **4/33** 抽样（测试范围） |
| IA 断言 | 10/12（2 项待全量 HTML 验证） |
| 增量页 vs examine2 | +租户 +SSO +API控制台 +系统租户设置 |

## 抽样 HTML

- [x] my-systems — 平台入口
- [x] plat-admin-api-console — 映射表
- [x] plat-admin-sso — OIDC 表单
- [x] runtime-list — 8 行业务数据 + 租户切换

## 待 Open Design 批量生成

其余 29 P0 页见 design-package §3

## 需补充到 portable 包（已做）

- [x] external-references.md
- [x] defaults 关键词扩页
- [x] lessons F1
- [x] learning 2026-06-23

## user-approval

- approved: false（测试 run，待用户审）
