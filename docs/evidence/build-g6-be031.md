# TASK-BE-031 构建证据

## 文件

- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/runtime/RuntimeModels.java`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/runtime/WorkflowRuntimeController.java`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/runtime/WorkflowRuntimeService.java`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/approval/ApprovalModels.java`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/approval/ApprovalController.java`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/approval/ApprovalService.java`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/todo/TodoModels.java`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/todo/TodoController.java`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/todo/TodoService.java`

## 端点

- `GET /api/v1/systems/{systemId}/flow-instances/{instanceId}`
- `POST /api/v1/systems/{systemId}/approval-tasks/{taskId}/approve`
- `POST /api/v1/systems/{systemId}/approval-tasks/{taskId}/reject`
- `POST /api/v1/systems/{systemId}/approval-tasks/{taskId}/transfer`
- `POST /api/v1/platform/todos/search`
- `POST /api/v1/systems/{systemId}/todos/search`
- `POST /api/v1/systems/{systemId}/todos/{todoId}/actions/{actionCode}`

## 验收点

- `WorkflowInstanceSnapshot` 返回实例、节点、当前审批任务、历史记录、业务目标、权限快照、trace 和 audit 信息。
- 审批动作使用内存 `ConcurrentHashMap` 保存 `taskId + action + idempotencyKey` 的首次结果；重复请求返回同一动作结果的重复视图，`workflowAdvanced=false`、`duplicate=true`，不会生成下一次推进。
- `reject` 结果包含拒绝原因；`transfer` 结果包含转交原因和目标人。
- 待办返回 `TodoSearchResult`，包含左侧 `typeTree` 和右侧分页 `page`，平台待办与系统待办入口分离，系统待办 target 只在当前系统成员上下文内跳业务对象。
- 模型字段通过 record JavaDoc `@param` 说明业务含义，public service 方法补充中文 JavaDoc。

## 验证命令

直接运行任务给定命令时，`D:\java\apache-maven-3.8.5\bin\mvn.cmd -v` 显示默认 Java 为 `1.8.0_271`，会导致 Java 21 项目误报语法错误；因此按项目环境规则仅在当前 PowerShell 命令中临时设置 JDK 21：

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

结果：`BUILD SUCCESS`，11 个 backend reactor 模块全部通过。

补充验证：

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -pl examine-flow -am -DskipTests compile
```

结果：`BUILD SUCCESS`，`examine-core`、`examine-flow` 通过。

```powershell
git diff --check -- backend/examine-flow/src/main/java/com/unique/examine/flow/manage/runtime backend/examine-flow/src/main/java/com/unique/examine/flow/manage/approval backend/examine-flow/src/main/java/com/unique/examine/flow/manage/todo docs/evidence/build-g6-be031.md
```

结果：无 trailing whitespace；`git diff --no-index --check` 对新增文件提示 Windows `LF will be replaced by CRLF` 换行符 warning。
