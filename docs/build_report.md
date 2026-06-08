# P6 构建验证修正报告

- 任务: VAL-004
- 执行时间: 2026-06-08
- 修正时间: 2026-06-08
- 执行角色: validator
- 修正结论: fail
- target: pm/frontend

## 修正原因

原报告把 `npm.cmd run build` 记录为前端 clean build pass，但当前 `frontend/package.json` 中 build 脚本仅为：

```json
{
  "build": "tsc --noEmit"
}
```

该命令只做 TypeScript 类型检查，不生成浏览器可部署的 `dist/` 产物。当前前端缺少 `index.html`、`src/main.*`、真实页面组件和应用挂载，因此不能判定为“前端可部署构建通过”。

## 子任务结果

| 子任务 | 记录 | 修正结论 | target |
| --- | --- | --- | --- |
| VAL-001 后端 clean compile | `docs/build/backend-clean-compile.md` | pass | 无 |
| VAL-002 前端类型检查 | `docs/build/frontend-clean-build.md` | partial，typed contract pass，deployable UI fail | frontend |
| VAL-003 契约同步检查 | `docs/build/contract-sync-check.md` | pass | 无 |

## 后端验证

| 项目 | 结果 |
| --- | --- |
| 命令 | `mvn -pl examine-web -am clean package -DskipTests` |
| JDK | `D:\java\jdk\jdk21` |
| Maven | `D:\java\apache-maven-3.8.5\bin` |
| 产物 | `backend/examine-web/target/unexamine.jar` |
| 部署包 | `dist/unexamine-deploy-20260608-110707.zip` |
| 结论 | 后端 jar 可试部署 |

## 前端验证

| 项目 | 结果 |
| --- | --- |
| `npm.cmd ci` | pass |
| `npm.cmd run build` | pass，但仅执行 `tsc --noEmit` |
| `index.html` | 缺失 |
| `src/main.*` | 缺失 |
| 真实浏览器页面组件 | 缺失 |
| `dist/` | 缺失 |
| 结论 | 前端不可部署，必须进入 P7 补真实 UI |

## Validator 修正结论

P6 构建验证不能作为“全项目可部署”依据。当前只允许后端 jar 试部署；完整项目部署必须等待 P7 生成前端 `dist/`，并完成浏览器 smoke/E2E。
