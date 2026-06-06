# Build Report - Step6 Validator

## 验证背景

- 验证时间：2026-06-05 06:52-06:53（Asia/Shanghai）
- 验证场景：contract_sync 微修复后重新验证。
- 本轮前端仅补充 `PLAT_PERMISSION_TYPE_INVALID`、`FLOW_ACTION_TYPE_INVALID` 两个错误码及契约映射；后端代码未因本轮 contract_sync 修改。
- 本轮仍重新执行后端 `clean compile`，未沿用上一轮后端构建结果。

## 后端验证命令

工作目录：`backend/`

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
mvn -version
mvn -DskipTests clean compile
```

## 后端验证结果

- 结果：通过。
- Maven 多模块结构检查：通过，以下 POM 均存在：
  - `backend/pom.xml`
  - `backend/examine-core/pom.xml`
  - `backend/examine-plat/pom.xml`
  - `backend/examine-module/pom.xml`
  - `backend/examine-flow/pom.xml`
  - `backend/examine-upload/pom.xml`
  - `backend/examine-app/pom.xml`
  - `backend/examine-web/pom.xml`
- Maven Reactor 顺序包含父工程与 7 个子模块：`examine`、`examine-core`、`examine-plat`、`examine-flow`、`examine-upload`、`examine-module`、`examine-app`、`examine-web`。
- 后端执行了 `clean compile`，各子模块 target 被清理后重新编译源码；日志出现 `Recompiling the module` 与具体源码文件数，不是增量构建的 `Nothing to compile`。
- Reactor Summary 全部 `SUCCESS`，最终 `BUILD SUCCESS`。
- 验证范围：Maven 父子模块工程结构、主源码编译、模块依赖编译链路。

## MyBatis-Plus 代码生成报告检查结果

- `backend/docs/mybatis-plus-generation.md` 存在且非空，文件大小 3605 bytes。
- 报告声明 MyBatis-Plus 生成器实际执行：`Actual generator execution: yes`。
- 报告记录 SQL 导入成功、执行语句 57 条、生成表 40 张，并列出 `un_platt_`、`un_module_`、`un_flow_`、`un_upload_`、`un_app_/un_openapi_`、`un_sys_/un_audit_` 到对应后端模块的映射。

## 前端验证命令

工作目录：`frontend/`

```powershell
$env:Path='D:\java\nodejs;'+$env:Path
node -v
npm.cmd -v
if (Test-Path dist) { Remove-Item -Recurse -Force dist }
if (Test-Path tsconfig.tsbuildinfo) { Remove-Item -Force tsconfig.tsbuildinfo }
if (Test-Path node_modules) { 'node_modules exists: yes' } else { npm.cmd install }
npm.cmd run build
```

## 前端验证结果

- 结果：通过。
- clean build：已在构建前删除 `frontend/dist` 与 `frontend/tsconfig.tsbuildinfo`，随后重新执行 `npm.cmd run build`。
- 构建脚本：`tsc -b && vite build`。
- 构建输出：`frontend/dist/index.html` 已重新生成。
- Vite 构建结果：3104 modules transformed，`built in 5.22s`。
- 非阻塞提示：Vite 提示主 JS chunk 大于 500 kB，属于构建体积 warning，不影响本轮 validator 通过结论。

## 前端 API 契约映射检查结果

- `frontend/docs/api-contract-map.md` 存在且非空，文件大小 11519 bytes。
- 契约映射声明页面只调用 `frontend/src/api/sdk.ts`，错误码、状态值、权限类型、流程动作类型集中定义。
- 本轮重点错误码 `PLAT_PERMISSION_TYPE_INVALID`、`FLOW_ACTION_TYPE_INVALID` 已出现在契约映射的 `ErrorCode` 列表和错误码同步场景说明中。

## 前后端错误码与枚举同步检查结果

- `docs/api.md` 已声明：
  - `PLAT_PERMISSION_TYPE_INVALID`：平台权限类型不合法。
  - `FLOW_ACTION_TYPE_INVALID`：流程任务处理动作不合法。
  - `PermissionType` 合法值：`MENU`、`BUTTON`、`API`、`FIELD`、`DATA_SCOPE`。
  - `FlowActionType` 合法值：`APPROVE`、`REJECT`、`TRANSFER`、`CANCEL`。
  - `ModuleStatus` 合法值：`DRAFT`、`PUBLISHED`、`DISABLED`。
- `frontend/src/api/enums.ts` 已同步：
  - `ErrorCode.PlatPermissionTypeInvalid = 'PLAT_PERMISSION_TYPE_INVALID'`
  - `ErrorCode.FlowActionTypeInvalid = 'FLOW_ACTION_TYPE_INVALID'`
  - `PermissionType`、`FlowActionType`、`ModuleStatus` 对应合法值均存在。
- `frontend/docs/api-contract-map.md` 已同步上述两个新增错误码及关键枚举值。
- 自动比对结果：API 错误码、关键枚举值、状态值在 `frontend/src/api/enums.ts` 与 `frontend/docs/api-contract-map.md` 中均无缺失。

## 前端源码目录清洁度检查结果

- 检查范围：`frontend/src/`。
- 未发现构建产物或旁路编译文件：
  - `*.vue.js`：无。
  - `*.js`：无。
  - `*.d.ts`：无。
- `frontend/tsconfig.tsbuildinfo` 在 clean build 后由 TypeScript 重新生成，位于 `frontend/` 根目录，不属于 `frontend/src/` 源码残留。

## Clean 构建说明与实际工具版本

- JDK：
  - 声明路径：`D:\java\jdk\jdk21`，可用。
  - 实际版本：Java 21.0.10 LTS, Oracle Corporation。
- Maven：
  - `docs/service_info.md` 未声明 Maven 绝对路径，仅要求在 `backend/` 执行 Maven 编译。
  - 实际使用：`D:\java\apache-maven-3.8.5`。
  - 实际版本：Apache Maven 3.8.5。
- Node/npm：
  - 声明路径：`D:\java\nodejs`，可用。
  - 实际 Node 版本：v24.14.0。
  - 实际 npm 版本：11.9.0。
  - PowerShell 下直接执行 `npm` 会命中 `npm.ps1` 执行策略限制；本轮使用同目录 `npm.cmd` 完成可复现构建。

## 失败日志摘要

- 后端：无失败。
- 前端：首次执行 `npm -v` / `npm run build` 被 PowerShell execution policy 拦截，关键错误为 `File D:\java\nodejs\npm.ps1 cannot be loaded because running scripts is disabled on this system`。
- 处理方式：改用 `npm.cmd -v` 与 `npm.cmd run build`，随后前端 clean build 通过。

## Validator 结论

通过。

本轮 contract_sync 后，后端 Maven 多模块结构完整，后端 clean compile 通过；前端清理旧构建产物后 clean build 通过；`PLAT_PERMISSION_TYPE_INVALID`、`FLOW_ACTION_TYPE_INVALID` 已同步到 `frontend/src/api/enums.ts` 和 `frontend/docs/api-contract-map.md`；`frontend/src/` 未混入 `.js`、`.vue.js`、`.d.ts` 残留。
