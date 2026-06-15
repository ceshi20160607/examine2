# 后端工程结构（base / manage / generator）

> 结构样例见只读备份：`.oldbk/backend/`  
> 新实现重建 `backend/` 时必须遵守本规范，**禁止**手写大批量贴表 CRUD。

## Maven 多模块

```
backend/
  pom.xml                    # 父 POM
  examine-core/              # 通用返回、异常、上下文、审计 base 表
  examine-plat/              # 平台账号、系统、租户
  examine-module/            # 应用、模块、字段、记录、RBAC、导出
  examine-flow/              # 流程模板、实例、待办
  examine-upload/            # 文件上传与引用
  examine-app/               # OpenAPI 客户端（表前缀 un_openapi_）
  examine-generator/         # MyBatis-Plus 代码生成（不在 web 包运行）
  examine-web/               # 启动类、Web 配置、全局装配
```

## 分层：base vs manage

| 层 | 包路径示例 | 谁写 | 内容 |
|----|------------|------|------|
| **base** | `com.unique.examine.module.base` | **generator 生成** | entity、mapper、mapper.xml、IService、ServiceImpl |
| **manage** | `com.unique.examine.module.manage` | **backend Agent 手写** | controller、业务 Service、BO/DTO、VO、权限、事务编排 |

### base 规则

- 只承载贴表基础 CRUD，**不**生成对外 Controller
- 表结构变更后 **重新跑生成器**，不手改 entity 字段（除非生成后极小补丁且记入 evidence）
- 生成路径：`{module}/src/main/java/.../base/`，XML 在 `{module}/src/main/resources/mapper/base/`

### manage 规则

- 对外 API 统一在 `manage.controller`
- 入参 BO/DTO，出参 VO，**不**直接暴露 `base.entity`
- 业务 Service 用真实语义命名：`RuntimeRecordService`、`SystemRbacService`，避免 `XxxManageImpl` 机械命名
- 默认 **不** 拆 interface + impl，除非多实现/SPI
- 编排、校验、权限、实体→VO 转换均在 manage

```text
examine-module/src/main/java/com/unique/examine/module/
  base/                    # ← generator，勿手写大批量
    entity/
    mapper/
    service/
    service/impl/
  manage/                  # ← 业务实现
    controller/
    service/               # 或 service/impl 当有明确需要
    bo/
    vo/
    enums/
```

## 表前缀 → 模块映射

| 表前缀 | 模块 | base 包 |
|--------|------|---------|
| `un_plat_` | examine-plat | `com.unique.examine.plat.base` |
| `un_module_` | examine-module | `com.unique.examine.module.base` |
| `un_flow_` | examine-flow | `com.unique.examine.flow.base` |
| `un_upload_` | examine-upload | `com.unique.examine.upload.base` |
| `un_openapi_` | examine-app | `com.unique.examine.app.base` |
| `un_sys_` / `un_audit_` | examine-core | `com.unique.examine.core.base` |

## examine-generator（省 Token 的关键）

**原则：** Agent 不手写 300+ 行 entity/mapper/service 样板，用生成器从数据库表一键产出 base。

### 命令即配置

每条命令显式传入：模块名、表前缀、base 包、Java 输出目录、mapper XML 目录。  
**不**维护中心表映射文件。

参考脚本：`.oldbk/backend/examine-generator/scripts/generate-base-crud.ps1`

### 标准流程（Build 阶段 TASK）

1. dba 冻结 `sql/init.sql` 并执行建库建表
2. backend 跑 `generate-base-crud.ps1`（或单模块命令）
3. backend **只写** `manage/` 业务与 Controller
4. `task-accept` 验证：base 文件带生成标记/可复跑生成器

### 单模块示例

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
cd backend
mvn.cmd -pl examine-generator -DskipTests exec:java `
  "-Dexec.mainClass=com.unique.examine.generator.cli.GeneratorCli" `
  "-Dexec.args=--backend-root . --module-name examine-module --table-prefix un_module_ --base-package com.unique.examine.module.base --source-root examine-module/src/main/java --mapper-xml-root examine-module/src/main/resources/mapper/base --execute"
```

### backend Agent 禁止

- 手写大批量 `base/entity`、`base/mapper`、`base/service`
- 在 `examine-web` 堆业务实现
- 修改生成器产物类名规避 Bean 冲突（优先启动扫描/BeanNameGenerator）

## 与 Gate 的关系

| Gate | backend 可做 |
|------|----------------|
| `design_user_approved = false` | ** nothing ** |
| `api_frozen = true` | 建骨架、跑 sql、生成 base、写 manage |
| 单 TASK | 仅 TASK 声明的 `outputs` 路径 |

## 旧代码位置

完整可编译旧工程在 `.oldbk/backend/`。新 `backend/` 重建时：

1. 可复制父 POM 与模块骨架
2. **必须**按新 `docs/api/api.md` 与 UI 重写 manage
3. base 以新生成器为准，不从旧 base 手抄
