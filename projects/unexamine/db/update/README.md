# 后续数据库变更

首版表结构冻结后，新增 `V19__meaningful_name.sql` 这类版本化文件。不得编辑已经执行过的版本。

- `V19__tenant_main_constraint.sql`：收紧主租户标记约束，明确拒绝 `is_main = 1` 且标记为空的无效数据。
- `V20__access_request_context.sql`：补齐无成员访问申请的身份源、外部主体、申请角色、审批角色/数据范围和全链路 traceId。
- `V21__sso_provider_version_lifecycle.sql`：增加企业身份源不可变版本、检测报告、发布人和当前发布版本指针，敏感凭据仍只保存 SecretRef。
- `V22__dictionary_version_lifecycle.sql`：增加字典不可变版本和发布指针，模块字段运行态只读取已发布字典快照。
- `V23__runtime_unique_index_key.sql`：增加运行态唯一键哈希及数据库唯一约束，按租户、模块、索引定义在并发写入时保证唯一。
- `V24__module_rule_test_result.sql`：持久化规则预演样例、分支结果、执行人和时间；规则定义变更后重置测试状态并阻止未经重测的发布。
- `V25__tenant_extension_publication_pointer.sql`：为租户扩展增加不可变版本发布指针和更新人，删除扩展只切断运行指针并保留可解释历史。
- `V26__module_rollback_version.sql`：允许同一草稿来源产生独立的回滚发布版本；每次回滚都复制目标快照为新的不可变版本，而不是只把发布指针指回历史行。
