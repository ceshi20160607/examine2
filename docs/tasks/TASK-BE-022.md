# TASK-BE-022

## meta

- task_id: TASK-BE-022
- type: implementation
- owner: backend
- phase: build
- parallel_group: G6
- depends_on: [TASK-BE-021, TASK-BE-033]

## goal

Implement import, export, draft, attachment, upload, and sequence APIs.

## inputs

- `docs/api/api.md`
- `backend/examine-module/src/main/java/com/unique/examine/module/base/**`
- `backend/examine-upload/src/main/java/com/unique/examine/upload/base/**`

## outputs

- `backend/examine-module/src/main/java/com/unique/examine/module/manage/importexport/**`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/draft/**`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/attachment/**`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/sequence/**`
- `backend/examine-upload/src/main/java/com/unique/examine/upload/manage/**`

## scope

### Do

- Implement precheck, confirm, export, draft save/get, attachment bind, upload result, and atomic sequence allocation.
- Long-running work returns `AsyncTask`.

### Do Not

- Do not implement external object storage production integration unless already configured.

## self_check_commands

```powershell
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] Import/export returns task progress and result/error files.
- [ ] Sequence allocation is atomic.

