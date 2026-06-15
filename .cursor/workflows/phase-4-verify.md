# Phase 4: Verify

## 入口

- Build 里程碑 TASK 全部 accepted

## 步骤

| # | 执行 | 输出 |
|---|------|------|
| 1 | test 新会话 | 执行 skill e2e-user-script |
| 2 | skill review-gate | `gate-verify.json` |
| 3 | **你** 部署试用 | `docs/decisions/user-trial.md` |

## 退出

- [ ] e2e-car-system pass
- [ ] review-gate pass
- [ ] 你试用无 P0 反馈
- [ ] `gates.user_script_passed = true`

## fail 时

- 不得打包
- 按 issue 回到 design / contract / build 对应 phase
- PM 不得单方面宣布完成
