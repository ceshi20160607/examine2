# G6 TASK-BE-022 Build Evidence

## 修改文件

- `backend/examine-module/src/main/java/com/unique/examine/module/manage/importexport/ImportExportController.java`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/importexport/ImportExportService.java`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/importexport/ImportExportModels.java`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/draft/DraftController.java`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/draft/DraftService.java`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/draft/DraftModels.java`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/attachment/AttachmentController.java`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/attachment/AttachmentService.java`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/attachment/AttachmentModels.java`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/sequence/SequenceController.java`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/sequence/SequenceService.java`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/sequence/SequenceModels.java`
- `backend/examine-upload/src/main/java/com/unique/examine/upload/manage/UploadManageController.java`
- `backend/examine-upload/src/main/java/com/unique/examine/upload/manage/UploadManageService.java`
- `backend/examine-upload/src/main/java/com/unique/examine/upload/manage/UploadManageModels.java`
- `backend/examine-upload/pom.xml`
- `docs/evidence/build-g6-be022.md`

未修改 `backend/examine-web/pom.xml`。当前 web 已依赖 `examine-module`，但未依赖 `examine-upload`；上传 manage controller 的运行时聚合需要 conductor 后续处理。

## 覆盖端点

- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/drafts`
- `GET /api/v1/systems/{systemId}/runtime/modules/{moduleId}/drafts/{draftId}`
- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/imports/precheck`
- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/imports/confirm`
- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/exports`
- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/attachments`
- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/sequences/allocate`
- `POST /api/v1/uploads/results`
- `GET /api/v1/uploads/files/{fileId}`
- `GET /api/v1/uploads/storage-policies`
- `POST /api/v1/uploads/files/{fileId}/access-result`

## 验收点

- 导入预检返回 `precheckId`、问题行、结果文件、错误文件和任务快照。
- 导入确认返回 `AsyncTask`，包含 `taskId/bizType/idempotencyKey/status/progress/retryable/cancelable/resultFile/errorFile/failureReason/partialSuccessCount/partialFailureCount/rollbackSupported/traceId/auditLogId/createdBy/createdAt`。
- 导出返回后台任务、预计结果文件和错误文件，体现脱敏模式与导出字段。
- 草稿保存和读取返回字段快照、子表行、附件引用、校验问题、权限快照和审计追踪。
- 记录附件绑定返回业务附件列表、预览/下载样例 URL、权限模式、重试标记和审计追踪。
- 上传接口只返回 `LOCAL_SAMPLE` 策略和样例访问结果，不连接生产对象存储。
- sequence 使用 `ConcurrentHashMap.computeIfAbsent + AtomicLong.getAndAdd(count)` 在当前样例进程内原子领取号段，不使用“查最大值 + 1”。

## 验证命令

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

首次执行失败：该 Maven 默认使用 `D:\java\jdk\jdk8\jre`，无法解析项目既有 Java 21 语法。

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

结果：通过，reactor 11 个模块均 `SUCCESS`。

```powershell
git diff --check -- backend/examine-module/src/main/java/com/unique/examine/module/manage/importexport backend/examine-module/src/main/java/com/unique/examine/module/manage/draft backend/examine-module/src/main/java/com/unique/examine/module/manage/attachment backend/examine-module/src/main/java/com/unique/examine/module/manage/sequence backend/examine-upload/src/main/java/com/unique/examine/upload/manage backend/examine-upload/pom.xml docs/evidence/build-g6-be022.md
```

结果：通过，无空白错误输出。
