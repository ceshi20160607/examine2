# 模块归属重构（examine-web 下沉到各模块）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `examine-web` 中的领域实现（module/flow 等）下沉回对应模块的 `manage/**`，让 `examine-web` 回归“入站层（controller + web 侧装配）”。

**Architecture:** `examine-web` controller 仅做 HTTP 适配，调用 `examine-module/manage/**` 与 `examine-flow/manage/**` 等模块提供的 manage 服务。逐批迁移，保证每一步 `mvn test` 通过并可独立回滚。

**Tech Stack:** Spring Boot 3.3.x, Maven 多模块, MyBatis-Plus, Redis, Jackson, Flyway.

---

## 迁移总原则（执行时严格遵守）

- **不改 CRUD 生成文件**；仅迁移/重命名手写逻辑与调用点。
- **每完成一个可独立验收的小点就 commit**（不要攒大包）。
- **每个 commit 前必须跑 `backend/mvn test`** 并确认 `BUILD SUCCESS`。
- **包结构约定**：领域手写逻辑统一放到各模块的 `manage/**`。

---

### Task 1: 下沉 module 导出与导出任务（export / export-jobs）

**Files:**
- Move (create new, then delete old):
  - From `backend/examine-web/src/main/java/com/unique/examine/web/manage/module/SystemModuleExportService.java`
  - To `backend/examine-module/src/main/java/com/unique/examine/module/manage/SystemModuleExportService.java`
  - From `backend/examine-web/src/main/java/com/unique/examine/web/manage/module/SystemModuleExportJobService.java`
  - To `backend/examine-module/src/main/java/com/unique/examine/module/manage/SystemModuleExportJobService.java`
  - From `backend/examine-web/src/main/java/com/unique/examine/web/manage/module/SystemModuleExportJobRunner.java`
  - To `backend/examine-module/src/main/java/com/unique/examine/module/manage/SystemModuleExportJobRunner.java`
- Modify (web controllers only):
  - `backend/examine-web/src/main/java/com/unique/examine/web/controller/module/SystemModuleExportController.java`
  - `backend/examine-web/src/main/java/com/unique/examine/web/controller/module/SystemModuleExportJobController.java`
- Modify (maven deps if needed):
  - `backend/examine-module/pom.xml`（增加对 `examine-upload` 的依赖：Runner 写入 UploadFile）
- Verify:
  - Run: `mvn test` in `backend/`

- [ ] **Step 1: 先写“编译期红灯”定位（可选但推荐）**

在迁移前，记录当前类引用点，确保迁移后不会遗漏。

（命令示例）

```bash
cd backend
mvn -q -DskipTests=false test
```

- [ ] **Step 2: 在 `examine-module` 新建目标包与类（先复制后改包名）**

将原类内容复制到新路径，首要改动仅为 `package` 与 import 的归属包名：

```java
package com.unique.examine.module.manage;
```

保持类名不变（便于 controller 注入替换）。

- [ ] **Step 3: 处理 `examine-module` → `examine-upload` 依赖**

如果 `SystemModuleExportJobRunner` 需要 `IUploadFileService/UploadFile`，则在 `backend/examine-module/pom.xml` 增加：

```xml
<dependency>
  <groupId>com.unique</groupId>
  <artifactId>examine-upload</artifactId>
  <version>${project.version}</version>
</dependency>
```

- [ ] **Step 4: 修改 `examine-web` controller 注入到新包**

把 controller 的 import 从：

```java
import com.unique.examine.web.manage.module.SystemModuleExportService;
```

改为：

```java
import com.unique.examine.module.manage.SystemModuleExportService;
```

`SystemModuleExportJobController` 同理改为注入 `com.unique.examine.module.manage.SystemModuleExportJobService`。

- [ ] **Step 5: 删除 `examine-web/manage/module/**` 旧实现文件**

确认 controller 引用已切走后，再删除旧文件，避免 Spring 扫描出现同名 Bean 冲突。

- [ ] **Step 6: 运行测试验证（必须）**

Run:

```bash
cd backend
mvn test
```

Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit（必须）**

```bash
git add backend/examine-module backend/examine-web
git commit -m "refactor: move module export/export-jobs manage into examine-module"
```

---

### Task 2: 下沉 module 领域“应用服务”（meta/rbac/dict/list-view/record facade）

**Files:**
- Move:
  - `backend/examine-web/src/main/java/com/unique/examine/web/service/ModuleRecordFacadeService.java`
  - `backend/examine-web/src/main/java/com/unique/examine/web/service/SystemModuleMetaService.java`
  - `backend/examine-web/src/main/java/com/unique/examine/web/service/SystemModuleRbacService.java`
  - `backend/examine-web/src/main/java/com/unique/examine/web/service/SystemModuleDictService.java`
  - `backend/examine-web/src/main/java/com/unique/examine/web/service/SystemModuleListViewService.java`
- To:
  - `backend/examine-module/src/main/java/com/unique/examine/module/manage/**`（保持类名，统一 `package com.unique.examine.module.manage;`）
- Modify controllers:
  - `SystemModuleMetaController`
  - `SystemModuleRbacController`
  - `SystemModuleDictController`
  - `SystemModuleListViewController`
  - `SystemModuleRecordController`
- Verify/Commit same as Task 1.

- [ ] **Step 1: 逐个类迁移（不要一把梭）**
- [ ] **Step 2: 每迁移一个类就跑 `mvn test`（可先局部 compile，再全量 test）**
- [ ] **Step 3: 每 1-2 个类（或一组同一 controller）提交一次**

---

### Task 3: 下沉 flow 引擎与图同步到 `examine-flow/manage/**`

**Files:**
- Move:
  - `backend/examine-web/src/main/java/com/unique/examine/web/service/FlowEngineService.java`
  - `backend/examine-web/src/main/java/com/unique/examine/web/service/FlowRecordGraphSyncService.java`
  - `backend/examine-web/src/main/java/com/unique/examine/web/service/FlowBizActionService.java`（归属确认：若仅是 flow 推进/日志等，放 flow；若是 web 侧编排可拆分）
- To:
  - `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/**`
- Modify controllers:
  - `SystemFlowController`
  - `SystemFlowQueryController`
  - `SystemFlowInboxController`
  - `OpenApiFlowController`

- [ ] **Step 1: 先迁移 `FlowEngineService`（核心）并修正注入点**
- [ ] **Step 2: 再迁移 `FlowRecordGraphSyncService`**
- [ ] **Step 3: 逐 controller 改注入与 import**
- [ ] **Step 4: `mvn test` + commit**

---

### Task 4: Web 层收口检查

**Goal:** 确保 `examine-web` 不再含领域 manage 实现。

- [ ] **Step 1: 确认 `backend/examine-web/.../manage/` 为空或仅保留纯 web 适配**
- [ ] **Step 2: `mvn test`**
- [ ] **Step 3: 最终提交**

```bash
git commit -m "refactor: keep examine-web as inbound layer only"
```

