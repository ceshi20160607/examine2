# TEST-010 P12 UI 可用性 E2E 分报告

执行时间：2026-06-10

验证对象：FE-024 返工提交 `2dd61b5 fix: address p12 ui rework review gaps`

## 执行环境

| 项目 | 结果 |
| --- | --- |
| 后端 | 使用低内存 JVM 参数启动到 `http://127.0.0.1:18080`，`/actuator/health` 返回 200 |
| 前端 | `npm.cmd run preview -- --host 127.0.0.1`，`http://127.0.0.1:4173/` 返回 200 |
| 浏览器 | in-app Browser 执行真实 UI 登录和页面操作 |
| 打包 | 未执行打包，未生成 `dist/unexamine-full-deploy-*.zip` |

后端启动命令：

```powershell
D:\java\jdk\jdk21\bin\java.exe -Xms64m -Xmx384m -Xss512k -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -Dserver.port=18080 -jar backend\examine-web\target\unexamine.jar
```

前端启动命令：

```powershell
npm.cmd run preview -- --host 127.0.0.1
```

## 已完成真实链路

| 链路 | 结果 | 证据 |
| --- | --- | --- |
| 登录 | pass | `platform_admin / 123123aa` 登录成功，跳转“我的系统”，`PLAT-001 操作成功` |
| 我的系统显式进入按钮 | pass | “我的系统”列表展示多个 `进入系统` 按钮 |
| 创建系统 | pass | 创建 `P12可用性系统20260610125451`，`PLAT-002 操作成功` |
| 进入系统总览 | pass | 点击新系统“进入”，落到 `/systems/2064692771705384961/overview`，`SYS-001 操作成功` |
| 系统角色基础配置 | pass with risk | 创建 `P12角色125451` 成功，加载权限目录并保存菜单授权成功，`RBAC-009 操作成功`；但操作权限下拉显示 `[object Object]` |
| 成员基础配置可见 | pass | 成员页 `MEM-001 操作成功`，当前管理员成员存在，页面展示“详情/编辑/停用/分配角色” |
| 应用创建 | pass | 创建 `P12应用125451`，`APP-002 操作成功` |
| 模块创建 | pass | 创建 `P12模块125451`，`MOD-002 操作成功`，模块 ID `2064693917962530818` |
| 字段创建 | pass | 创建必填文本字段 `标题125451 / title125451`，`FIELD-002 操作成功` |
| 页面配置与发布 | pass | 保存默认页面、保存菜单动作、发布检查、发布模块均成功，发布到 `v2` |
| 导出 | pass | 导出页识别模块已发布；创建模板 `P12导出模板125451` 成功；创建导出任务成功，任务状态 `SUCCESS` |
| 审计页面加载 | partial | 正确路径 `/systems/2064692771705384961/audit` 可打开，`AUD-001 操作成功`；按 requestId 检索未返回日志 |

## 阻塞点

TEST-010 主链路卡在“运行台新建记录 / 查询记录 / 提交审批”。

### 1. 运行台 UI 新建记录失败

操作：

1. 进入 `/systems/2064692771705384961/runtime/modules/2064693917962530818`
2. 页面自动加载 schema 和列表，`RUN-002/RUN-003 操作成功`
3. 在字段 `标题125451` 输入 `P12记录125451`
4. 点击“新建记录”

结果：

```text
COMMON_PARAM_INVALID: 参数不合法，请求号 req_20260610_bd19063e13764f1c
```

### 2. 同一数据用后端 API 直调可以创建成功

为定位失败范围，使用相同系统、租户、成员上下文直调 RUN-004：

```json
{
    "remark": "direct probe",
    "values": [
        {
            "fieldCode": "title125451",
            "value": "P12 API record",
            "displayValue": "P12 API record"
        }
    ]
}
```

结果：后端返回 `COMMON_OK`，生成记录 `2064694975950548994`。

这说明后端、模块 schema 和测试数据可用，运行台 UI 的新建记录提交组装或前端错误处理存在问题。

### 3. UI 查询后端已创建记录失败

回到运行台输入关键字 `P12 API record` 并点击“查询记录”，或点击“加载运行台”，页面均报：

```text
Cannot read properties of undefined (reading 'find')
```

因此 UI 不能展示后端已创建记录，无法继续从真实 UI 执行记录详情、提交审批、流程处理。

## Reviewer 预审点复验

| 检查项 | 结果 |
| --- | --- |
| 我的系统显式进入按钮 | pass |
| `frontend/src/App.ts` 无 `window.prompt` | pass |
| 平台账号/角色页面内编辑授权 | pass |
| 系统角色权限多选 | pass，但操作权限选项显示 `[object Object]` |
| 运行台 Tabs 可切换 | pass |
| 流程 Tabs 可切换 | pass |
| 导出 Tabs 可切换 | pass |

## 结论

TEST-010 全量 E2E 结论：blocked/rework。

已验证 P12 大部分真实链路可跑通到“模块发布”和“导出任务成功”，但运行台 UI 新建记录失败，且 UI 无法展示后端已创建记录，导致提交审批、流程处理主链路无法继续。

target：frontend。

建议修复点：

- 修复运行台 `RUN-004` UI 提交参数组装或字段值映射，确保页面新建记录可成功。
- 修复运行台记录列表模型处理中的 `undefined.find` 异常，确保 `RUN-003` 返回记录后可渲染列表。
- 修复系统角色操作权限多选展示 `[object Object]` 的可用性问题。

## 返工修复记录

执行时间：2026-06-10

| 问题 | 修复 |
| --- | --- |
| 运行台 UI 新建记录提交空值或缺少必填字段 | `normalizeFormSchema` 增加字段定义兜底：后端页面 schema 缺少 `formSections` 或返回空分组时，按 `fieldDefinitions` 自动生成“基础信息”表单区。 |
| 运行台查询记录时报 `Cannot read properties of undefined (reading 'find')` | `normalizeListSchema` 和 `normalizeDetailSchema` 增加字段定义兜底，避免列表/详情模型在空 schema 下构造异常。 |
| 系统角色操作权限多选显示 `[object Object]` | `catalogOperationOptions` 兼容字符串和对象两种权限目录结构，优先取 `operationCode/code/permissionCode/value` 与 `operationName/name/label`。 |

自检：

```powershell
npm.cmd run build
```

结果：pass。

当前结论：frontend rework fixed，TEST-010 仍需重新执行真实浏览器全链路后才能改为 pass。未执行打包。

## 2026-06-11 复测记录

复测方式：本机 Chrome headless DevTools Protocol 真实浏览器 DOM 操作。后端以低内存参数启动在 `http://127.0.0.1:18080`，前端 preview 启动在 `http://127.0.0.1:4173/`，前端 API 基址使用 `baseUrl=http://127.0.0.1:18080`。本轮未执行打包。

复测范围：

| 链路 | 结果 | 证据 |
| --- | --- | --- |
| 登录与进入系统 | pass | `platform_admin / 123123aa` 登录后从“我的系统”进入 P12 系统 `2064692771705384961` |
| 运行台 schema 与列表加载 | pass | 进入 `/systems/2064692771705384961/runtime/modules/2064693917962530818` 后出现字段 `runtime_title125451`，列表可渲染历史记录 `P12 API record` |
| 运行台新建记录 | pass | 新建 `P12 UI retest 20260610172945`，记录出现在 `.data-row.records` 列表行中 |
| 运行台查询记录 | pass | 关键字查询 `P12 UI retest 20260610172945` 后，记录仍在 `.data-row.records` 中可见，无 `Cannot read` 异常 |
| 提交审批 | pass | 点击记录行“提交”后，页面显示 `RUN-008 操作成功：COMMON_OK`，记录状态变为 `SUBMITTED` |
| 流程工作台 | pass | 进入 `/systems/2064692771705384961/flow/workbench` 并刷新，`FLOW-007/FLOW-013/FLOW-014/FLOW-017 操作成功`，无 4xx/5xx 响应 |
| 系统角色权限多选 | pass | 加载权限目录后，操作权限下拉显示“系统管理全部操作”，未出现 `[object Object]` |

浏览器断言摘录：

```text
P12 UI retest 20260610172945
SUBMITTED
RUN-008 操作成功：COMMON_OK
FLOW-007/FLOW-013/FLOW-014/FLOW-017 操作成功
```

网络与控制台结果：

- `failedResponses`: 空。
- `networkFailures`: 空。
- `consoleErrors`: 空。

TEST-010 复测结论：pass。

说明：上一轮已经覆盖登录、创建系统、角色配置、应用/模块/字段/页面发布、导出任务成功、审计页面加载；本轮复测专门闭环返工后的阻塞点。P12 仍未进入打包，下一步交给 VAL-008 执行 clean build。
