# TASK-BE-034 G4 后端自检证据

## 实现概要

- 新增 `manage/sso`：平台身份源配置、测试、发布，系统 SSO 继承策略查询/更新，组织/成员映射预检，成员绑定确认。
- 新增 `manage/secret`：`SecretRef` 元数据查询，`SecretRotationJob` 创建与查询，复用 `AsyncTaskView` 表达后台任务边界。
- 新增 `manage/nomember`：`NoMemberAccessRequest` 创建、分页查询、审批、拒绝。
- 所有 Secret 响应只返回 `secretRefId/refType/version/expiresAt/rotationStatus/lastUsedAt/displayName`，不返回 `storageRef`、密钥材料或明文 secret。
- 无成员映射申请在 `APPROVED` 且具备 `systemMemberId/roleIds/dataScope` 前，`businessAccessAllowed=false`，并返回 `status/disabledReason/traceId`。

## 端点列表

- `GET /api/v1/platform/identity-providers`
- `POST /api/v1/platform/identity-providers`
- `PATCH /api/v1/platform/identity-providers/{providerId}`
- `POST /api/v1/platform/identity-providers/{providerId}/test`
- `POST /api/v1/platform/identity-providers/{providerId}/publish`
- `GET /api/v1/systems/{systemId}/sso/policies`
- `PATCH /api/v1/systems/{systemId}/sso/policies`
- `POST /api/v1/systems/{systemId}/sso/org-sync/precheck`
- `POST /api/v1/systems/{systemId}/sso/member-bindings/confirm`
- `POST /api/v1/systems/{systemId}/no-member-access-requests`
- `GET /api/v1/systems/{systemId}/no-member-access-requests`
- `POST /api/v1/systems/{systemId}/no-member-access-requests/{requestId}/approve`
- `POST /api/v1/systems/{systemId}/no-member-access-requests/{requestId}/reject`
- `GET /api/v1/secrets/{secretRefId}`
- `POST /api/v1/secrets/{secretRefId}/rotation-jobs`
- `GET /api/v1/secrets/rotation-jobs/{jobId}`

## 命令结果

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend/pom.xml -pl examine-plat -am -DskipTests compile
```

- 结果：`BUILD SUCCESS`
- 备注：该次 Maven 增量编译输出 `Nothing to compile`。

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend/pom.xml -pl examine-plat -am -DskipTests clean compile
```

- 结果：`BUILD SUCCESS`
- 备注：重新编译 `examine-core` 74 个源文件、`examine-plat` 112 个源文件。

```powershell
git diff --check -- backend/examine-plat/src/main/java/com/unique/examine/plat/manage/sso backend/examine-plat/src/main/java/com/unique/examine/plat/manage/secret backend/examine-plat/src/main/java/com/unique/examine/plat/manage/nomember docs/evidence/build-g4-be034.md
```

- 结果：无输出。
- 备注：新增文件未跟踪，另用 `git diff --check --no-index` 对新增 Java 文件逐个检查；仅出现 Windows `LF will be replaced by CRLF` 换行提示，无尾随空白等内容 warning。

## 残余风险

- 当前延续 G3 contract-first 风格，服务返回示例/任务边界数据，尚未接入真实数据库持久化、异步任务调度和审计落库。
- Secret 明文托管、加密边界、双写验证窗口和真实回滚执行仍需后续 Secret 管理集成任务实现；本次只暴露 `SecretRef` 元数据和轮换任务状态机。
- `NoMemberAccessRequest` 审批通过后是否自动触发系统切换仍是 API 文档 P2 待复核项；本次返回 `businessAccessAllowed=true` 和绑定信息，由前端/调用方重新发起系统切换更稳妥。
