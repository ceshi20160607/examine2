# 需求记录 / 变更日志（Log）

本文用于记录**需求澄清、范围变更、关键决策**，按时间顺序追加，便于追溯“为什么这么做”。  
产品与边界以 `README.md` 为准；若 README 需要调整，先在此记录，再更新 README。

---

## 记录规则

- **只追加不改历史**：如需修正，用“更正”条目追加
- **一条记录一个主题**：尽量可搜索（关键词明确）
- **每条包含**：时间、背景、变更点、影响范围、结论、后续动作

---

## 2026-04

### 2026-04-16 需求文档收敛

- **背景**：文档过多且“计划/排期/进度”多处维护导致混乱，无法一眼看出已完成项。
- **变更**：文档收敛为 3 份（`README.md` + 开发任务清单 + 需求 log）。
- **影响范围**：后续进度只更新 `docs/development-tracker.md`；需求澄清与变更只追加到本文；README 只维护总纲与里程碑摘要。
- **结论**：
  - `README.md` 是需求根本与总纲（唯一）
  - `docs/development-tracker.md` 是开发任务（唯一进度表）
  - `docs/requirements-log.md` 是需求记录（唯一变更日志）
- **后续动作**：后续任何新增模块/接口/里程碑口径调整，必须先追加本 log，再同步 README 与 tracker。

### 2026-04-16 业务数据存储：统一 EAV（一行一字段）

- **背景**：列表 DSL 曾用 `data.*` 路径查 JSON，与主文档「record_data EAV」不一致。
- **变更**：`un_module_record_data` 改为 **EAV**：`record_id` + `field_code` 唯一，每行 `value_text`；创建接口仍接收 JSON `data` 对象，服务端拆成多行写入；`POST /v1/system/records/query` 中动态条件使用 **field_code**（与 `un_module_field.field_code` 对齐），保留字仍为 `id` / `createTime` / `updateTime`。
- **影响范围**：需按新版 `docs/sql/05_module_ddl.sql` 建库；旧库可参考 `docs/sql/15_module_record_data_eav_alter.sql` 手工迁移（脚本内为注释指引）。
- **结论**：对外语义统一为「字段一行」，不再使用 `data.xxx` 形式的 DSL 字段名。
- **后续动作**：后续可按模型元数据校验 `field_code` 是否存在、以及扩 typed 列/索引。

---

## 2026-05

### 2026-05-18 列表查询批量附带 EAV + 开放 API 签名

- **背景**：记录列表 Web/移动端对每条记录调 `getRecord` 造成 N+1；开放 API 长期明文传 SK。
- **变更**：`POST /v1/system/records/query` 增加 `includeFieldCodes`，响应 `list[].data`；开放 API 支持 HMAC v1 签名（`sign_secret_enc`）；EAV 增加等值查询索引。
- **影响范围**：Flyway `V21`；旧开放凭证需轮换 SK 后可用签名模式。
- **结论**：列表展示走单次 query；第三方推荐签名头，兼容 `X-Secret`。
- **后续动作**：typed-value 列、关系列表同样批量附带字段（按需）。

### 2026-05-16 H-5 导出任务与列表多列

- **背景**：导出任务页仅静态列表；记录列表仅「摘要」一列，未用页面 `columnFieldCodes`。
- **变更**：`ExportJobsView` 状态筛选、3s 轮询待处理任务、鉴权 `fetch` 下载；`RecordsListView` 按 runtime 列 + `includeFieldCodes`；`RelationsView` 展示关系 ID 与 `relationId` 配置提示。
- **结论**：tracker H-5 ✅；移动端列表此前已支持 `columnFieldCodes`。

### 2026-05-16 I 阶段功能收官

- **背景**：里程碑 A–H 主体完成，但开放 API 记录仅 create/update；Web 缺创建系统、RBAC dataScope、导出发起、签名/评分字段等，无法单独走通生产冒烟。
- **变更**：`OpenApiModuleRecordController` 补齐 query/detail/delete/query-by-relation/history；Web `ExportsView`/`SystemsView`/`RbacView`/`PlatformInboxView`/`SignatureField`/`RatingField`；契约文档与 `mobile-api-coverage` 同步。
- **结论**：tracker I-1～I-5 ✅；项目 v1 功能面闭环，typed-value 列与 Web 筛选模板页列为后续迭代。

