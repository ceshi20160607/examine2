# P2-P3-COMBINED-DEMO 验收记录

- level: slice
- scope_id: `P2-P3-COMBINED-DEMO`
- checked_at: `2026-07-15T18:04:10+08:00`
- engineering_verdict: pass
- independent_test_verdict: pass
- independent_test_reviewed_at: `2026-07-15T19:04:26+08:00`
- independent_test_boundary: 只接受 P2/P3 组合展示；不扩展到动态记录、P4-P10 或最终系统
- status_ref: `.cursor/session/state.json#deliveryHistory.P2-P3-COMBINED-DEMO`

## Delivers

复现“登录 -> 平台治理 -> 系统/配置发布 -> 普通成员授权系统 -> 已发布模块入口”，补齐 VS1-VS3 只按技术层验收后缺少的组合展示。

## Does Not Deliver

模块页明确显示 `recordsAvailable=false` 和“业务记录将在动态数据运行节点启用”。本 slice 不证明动态记录 CRUD、Flow、工作、文件、报表、OpenAPI、AI 或最终系统完成；这些仍为 `P4-A1..P10`。

## Evidence Matrix

| category | result | evidence | boundary |
|---|---|---|---|
| API | pass | 真实 member 登录 `OK`；系统列表 2；切换后 context=`SYSTEM`；runtime navigation groups=1；module definition 返回 `mobile_order`、3 pages、`recordsAvailable=false` | 不证明动态记录 API |
| DB | pass | MySQL 中系统 `2077227956775075842` 为 ACTIVE；成员 `member_a470891f` 映射 ACTIVE；发布 version=1、checksum=`51e20e...54d8`；模块 `mobile_order` ENABLED | 不证明 VS4 表存在 |
| UI | pass | 平台 Root 可进入系统治理；普通成员只看到可进入系统，进入后只看到授权组“移动业务”和模块“移动工单” | 不证明配置编辑全范围 |
| Browser | pass | 应用内浏览器真实操作完成管理员治理入口和普通成员发布模块入口；390x844 下 `innerWidth=390`、`scrollWidth=390`；console warn/error 为 0 | 独立 Playwright runner 因 Chromium 1228 下载环境阻塞未作为 pass 证据 |
| Restart | pass | 后端 jar SHA-256 `CBF52D...F6F38` 以 PID 31792 重启；`GET /management/health` 为 UP；重启后登录、系统切换、navigation/definition 和 DB 读回均成立 | 不证明发布制品回滚 |

## Existing Accepted Scope

- `.cursor/session/evidence/vs1/acceptance.md`
- `.cursor/session/evidence/vs2/acceptance.md`
- `.cursor/session/evidence/vs3/acceptance.md`
- `.cursor/session/evidence/vs3/ordinary-member-runtime.png`

## Next

进入 `P4-A1-READ-RUNTIME`。P2/P3 组合演示不作为 P4 coding 的长期前置设计，也不重复实现现有代码。
