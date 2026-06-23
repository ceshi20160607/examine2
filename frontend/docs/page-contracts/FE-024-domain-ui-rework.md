# FE-024 业务域 UI 可用化改造

- 所属期次：P12-uiux-frontend-rework
- 负责人：frontend
- 状态：done
- 设计输入：`docs/ui/ui-design.md`、`docs/ui/prototypes/page-prototypes.md`

## 改造范围

| 页面域 | 改造结果 |
| --- | --- |
| 应用配置 | 应用、模块、字段、页面与发布改为步骤式工作台，左侧步骤导航、中间主内容、右侧发布提示。 |
| 运行台 | 运行列表改为业务工作区，记录详情增加“详情、附件、审批、历史、关联” Tabs。 |
| 流程 | 流程模板增加模板设计说明；流程工作台增加审批工作台说明和待办、抄送、我的申请、流程实例 Tabs。 |
| 文件中心 | 上传区改为独立卡片，说明文件预览、下载和引用关系。 |
| 导出 | 增加模块发布状态提示；导出模板和导出任务以 Tabs 组织，失败任务显示失败原因。 |
| OpenAPI | 客户端接入改为接入卡片，强调密钥只展示一次，Scope、IP 白名单和限流集中维护。 |
| 审计运维 | 审计检索增加 requestId 优先追踪说明；运维巡检增加异常说明和处理建议提示。 |

## Reviewer 预审返工

| 问题 | 处理结果 |
| --- | --- |
| 我的系统整行隐式点击，不符合显式进入按钮原型 | 改为系统行内“进入系统”主按钮。 |
| 平台账号、平台角色使用 `window.prompt`，偏调试形态 | 账号编辑、重置密码、角色分配、角色编辑和角色授权均改为页面内表单。 |
| 系统角色授权仍是编码文本输入 | 改为基于权限目录的操作权限和菜单权限多选。 |
| 运行台、流程、导出 Tabs 只是静态外观 | 改为有状态的可切换 Tabs，每个 Tab 只展示对应内容。 |

## 前端实现

- `frontend/src/App.ts`
  - 新增 `renderConfigWorkbench`，统一应用配置步骤工作台。
  - 新增 `renderRuntimeDetailTabs`，统一运行台记录详情分区。
  - 新增平台账号/角色页面内编辑和授权表单，移除浏览器 prompt。
  - 新增有状态 Tabs：运行台详情、流程工作台、导出模板/任务。
  - 流程、文件、导出、OpenAPI、审计、运维页面从工程型 `admin-page` 调整为业务域 `domain-page`。
- `frontend/src/styles.css`
  - 新增步骤导航、发布提示、任务卡、Tabs、上传区、导出状态卡、多选控件和响应式布局样式。

## 自检结果

| 项目 | 结果 |
| --- | --- |
| 前端构建 | `npm.cmd run build` pass |
| 生产预览 | `http://127.0.0.1:4173/` HTTP 200 |
| 系统总览深链 | `http://127.0.0.1:4173/#/systems/demo/overview` HTTP 200 |
| prompt 扫描 | `rg -n "window\\.prompt|prompt\\(" frontend\\src\\App.ts` 无结果 |

## 未完成但必须由后续角色验证

- TEST-010 还必须执行真实浏览器 UI 可用性 E2E，并记录截图或 DOM 断言。
- VAL-008 还必须执行 P12 clean build 和后端 package，但不得生成最终部署包。
- REV-008 还必须重新审查 UI/UX 和完整可用性，通过后才能恢复 `fullProjectDeployable=true`。
