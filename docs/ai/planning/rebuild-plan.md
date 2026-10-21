# 第 16 次重构实施计划

## 唯一依据

产品需求为 `docs/user_requirement.md`；表名和用途为 `docs/表意义.md`；`docs/old_table1.sql` 只研究早期字段与审批，不执行。旧代码和旧验收只在 Git 标签 `pre-rebuild-16-20261021` 中可恢复，不引用作本轮完成证据。`70a3979a` 只参考 Controller、Service、Mapper 调用和 `simple` 模板的五个基础方法，不带入旧 CRM 类型。

## 固定工程边界

- 根目录保留 `docs/`、`template_base/`、`back/`、`font_web/`；`back/` 是 Maven 多模块后端，全部模块名为 `examine-*`，唯一入口是 `examine-web`。
- 每个业务包下 `base` 放生成的 Entity、Mapper、IService、ServiceImpl；`manage` 放特定业务规则及 HTTP 入口。生成模板必须实际实现 `queryPageList`、`addOrUpdate`、`queryById`、`information`、`deleteByIds`，不能以 MyBatis-Plus 同名包装或空壳冒充分层。
- 使用新库 `unexamine_r16`，本机既有 MySQL/Redis，不运行新容器。前端等后端接口真实验收后再开发。

## 顺序与可观察结果

1. **规则与模板**：从 `70a3979a` 提取五种基础操作的输入、输出、错误和布局语义；新生成器只依赖 `template_base`，以独立样表渲染、编译和调用验证模板。验收点是生成的 ServiceImpl 真正查询、保存、读取、返回布局及删除，Controller 没有 Mapper/SQL/业务判断。
2. **表与归属**：按《表意义.md》逐张确定业务模块、必要字段、中文备注和关系；新建 `unexamine_r16`，一次只执行当前批次的幂等 DDL，已有数据通过迁移而非删表。先完成平台账号、系统/租户与模块记录的最短业务旅程，再补齐其余表。
3. **生成与业务**：从真实库运行生成器，生成代码只进入对应模块 `base`。`manage` 只处理会话授权、跨表约束和状态编排；每批最多 240 分钟，跨模块依赖串行。
4. **后端验收**：每批通过编译后，用 9999 实际接口核对成功、持久化、越权和失败；整体接口清单覆盖列表、表头排序、场景、表单布局与保存、流程、审批、工作、展示等当前基础能力。未测不标完成。
5. **前端**：用户验收后端业务边界和接口后，在 `font_web/` 实现唯一前端并验证实际页面。

## 当前断点

第 15 次源码、派生文档与生成器已移出活动工作区；当前仅需求源、表用途和旧 SQL 参考保留在 `docs/`。五方法模板已通过独立样表的实际生成与编译；带布局的 `information` 已运行验证 50% + 50% 同行。下一步仍需设计首批表，并在真实 MySQL 验证五个方法的持久化结果。历史 `unexamine_r15` 与其 HTTP 记录不计入第 16 次进度。
