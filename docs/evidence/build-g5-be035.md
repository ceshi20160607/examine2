# G5 TASK-BE-035 OpenAPI APIs 自检证据

## 任务范围

- Worker：G5 backend worker
- Task：`TASK-BE-035`
- 允许输出：`backend/examine-app/src/main/java/com/unique/examine/app/manage/openapi/**`
- 额外允许：`backend/examine-app/pom.xml`、`docs/evidence/build-g5-be035.md`

## 本次端点

外部应用 CRUD 与配置：

- `GET /api/v1/systems/{systemId}/openapi/apps`
- `POST /api/v1/systems/{systemId}/openapi/apps`
- `GET /api/v1/systems/{systemId}/openapi/apps/{externalAppId}`
- `PATCH /api/v1/systems/{systemId}/openapi/apps/{externalAppId}`
- `DELETE /api/v1/systems/{systemId}/openapi/apps/{externalAppId}`
- `GET /api/v1/systems/{systemId}/openapi/apps/{externalAppId}/scopes`
- `PUT /api/v1/systems/{systemId}/openapi/apps/{externalAppId}/scopes`
- `GET /api/v1/systems/{systemId}/openapi/apps/{externalAppId}/rate-limit`
- `PATCH /api/v1/systems/{systemId}/openapi/apps/{externalAppId}/rate-limit`
- `POST /api/v1/systems/{systemId}/openapi/apps/{externalAppId}/rotate-secret`

调用日志：

- `GET /api/v1/systems/{systemId}/openapi/call-logs`
- `POST /api/v1/systems/{systemId}/openapi/call-logs/search`

## 合同覆盖

- 写接口支持 `Idempotency-Key` 请求头，也支持请求体 `idempotencyKey` 字段，响应的 `OperationMetadata` 回显最终采用的幂等键。
- OpenAPI 密钥响应只返回 `OpenApiSecretRefVO`：`secretRefId/refType/version/expiresAt/rotationStatus/lastUsedAt/displayName/activeVersion/nextRotationDueAt`。
- 创建和轮换请求只表达 `secretMaterialRef/newMaterialRef` 这类托管引用，不在响应中返回明文 app secret。
- 调用日志筛选模型 `OpenApiCallLogQuery` 覆盖 `externalAppId/externalAppCode/scope/result/startTime/endTime/traceId`。
- 限流元数据使用 `RateLimitMetadata` 表达维度、窗口、阈值、突发量、溢出策略、状态和版本。

## 自检命令

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend/pom.xml -pl examine-app -am -DskipTests compile
```

结果：

- `examine-core`：SUCCESS
- `examine-app`：SUCCESS
- Reactor：BUILD SUCCESS
- 完成时间：`2026-06-23T23:37:05+08:00`

## diff 检查

命令：

```powershell
git diff --check
```

结果：退出码 0；仅报告既有文件的 LF/CRLF warning：

- `.cursor/session/issues/registry.jsonl`
- `docs/design/pre-coding-readiness.md`
- `docs/design/user-approval.md`

当前仓库中 `backend/` 和 `docs/evidence/` 处于未跟踪状态，`git diff --check` 不会覆盖未跟踪新文件；已通过 Maven 编译校验新增 Java 文件。

## 残余风险

- 本任务延续 G3/G4 manage 层风格，当前是合同优先 API stub，尚未接真实数据库 CRUD、`un_sys_idempotency_key` 幂等持久化或 Secret 托管后端。
- Secret 轮换返回 `AsyncTaskView` 和阶段元数据，但没有执行真实双写验证、切换生效、观察期和旧版本停用。
- 未修改 `backend/examine-web/pom.xml`，因此 web 聚合模块是否装配 `examine-app` 需要由拥有该文件的任务处理。
