# P15 多角色梳理协调

状态：`in_progress`

## 目标

P15 不再由单一 PM 视角直接补丁。必须先由多个角色围绕同一个真实用户剧本进行反向审查，再由 PM 汇总裁决。

核心剧本：

平台管理员创建“车”系统 -> 在车系统内开通 `che` 普通成员 -> 创建车辆模块、字段、页面和菜单 -> 发布并授权 -> `che` 登录后直接进入车系统业务运行台 -> `che` 新增、查询、编辑车辆数据 -> `che` 看不到平台管理、建模、字段设计、发布和权限入口。

## 角色与输出

| 角色 | agent | 责任 | 输出 |
| --- | --- | --- | --- |
| PM | Socrates | 重新裁决产品定位、阶段边界、不打包条件和角色责任 | `docs/understanding/p15_pm_rebaseline_review.md` |
| UI/UX | Dalton | 重画信息架构、页面主动作、空态/错误态/权限态和业务文案 | `docs/understanding/p15_uiux_rebaseline_review.md` |
| Frontend | Kierkegaard | 梳理前端入口、导航、可见性、分页、新建入口和技术 ID 暴露 | `docs/understanding/p15_frontend_rebaseline_review.md` |
| Backend | Newton | 梳理账号/成员/权限/系统上下文模型如何包装成业务动作 | `docs/understanding/p15_backend_rebaseline_review.md` |
| Test | Copernicus | 设计真实浏览器 E2E、失败判定和不打包条件 | `docs/understanding/p15_test_rebaseline_review.md` |
| Reviewer | Sartre | 主动推翻可用结论，定义 P15 审查清单和 pass/fail 标准 | `docs/understanding/p15_reviewer_rebaseline_review.md` |

## 协调规则

1. 各角色本轮只梳理，不改代码、不打包。
2. 输出必须围绕“车系统 / che 普通成员”剧本，不允许泛泛列功能清单。
3. frontend/backend 的实现建议必须服务于 UI/UX 和 PM 冻结后的主流程。
4. test 和 reviewer 的失败判定优先级高于 PM 自我判断。
5. 如果角色结论冲突，由 PM 裁决；PM 不能裁决时写入 `docs/issues/user_questions.md`。
6. P15 冻结前，当前 4 个代码 partial 不计入完成。

## 当前 partial 代码

以下文件已有 P15 草稿改动，但在多角色梳理完成前不计入交付：

- `backend/examine-module/src/main/java/com/unique/examine/module/manage/bo/MemberInviteBO.java`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/service/impl/SystemRbacServiceImpl.java`
- `frontend/src/App.ts`
- `frontend/src/pages/system/types.ts`

## PM 汇总入口

所有角色输出完成后，Orchestrator/PM 必须汇总为：

1. P15 最终产品裁决。
2. P15 UI/UX 冻结任务。
3. P15 frontend/backend 实现任务。
4. P15 test/reviewer 验收任务。
5. 是否需要撤销、保留或重做当前 partial 代码。
