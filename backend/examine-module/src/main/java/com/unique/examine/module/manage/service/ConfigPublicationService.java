package com.unique.examine.module.manage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.DynamicPermissionFacade;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.OutboxEvent;
import com.unique.examine.core.api.OutboxFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.manage.api.ConfigRequests;
import com.unique.examine.module.manage.api.ConfigViews;
import com.unique.examine.module.manage.security.ConfigSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ConfigPublicationService {
    private final JdbcTemplate jdbc;
    private final IdService ids;
    private final ConfigSnapshotService snapshots;
    private final ConfigCheckService checks;
    private final ConfigMutationSupport mutations;
    private final DynamicPermissionFacade permissions;
    private final OperationAuditFacade audit;
    private final OutboxFacade outbox;
    private final DraftRevisionCoordinator revisions;
    private final RuntimeSchemaProjectionService runtimeSchemas;
    private final ObjectMapper objectMapper;

    public ConfigPublicationService(JdbcTemplate jdbc, IdService ids, ConfigSnapshotService snapshots,
                                    ConfigCheckService checks, ConfigMutationSupport mutations, DynamicPermissionFacade permissions,
                                    OperationAuditFacade audit, OutboxFacade outbox,
                                    DraftRevisionCoordinator revisions, RuntimeSchemaProjectionService runtimeSchemas,
                                    ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.ids = ids;
        this.snapshots = snapshots;
        this.checks = checks;
        this.mutations = mutations;
        this.permissions = permissions;
        this.audit = audit;
        this.outbox = outbox;
        this.revisions = revisions;
        this.runtimeSchemas = runtimeSchemas;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ConfigViews.PublishResult publish(ConfigSession session, ConfigRequests.PublishConfig request,
                                             String idempotencyKey, RequestContext context) {
        return mutations.idempotent(session.systemId() + ":config:publish", idempotencyKey, request,
                ConfigViews.PublishResult.class,
                () -> publishNow(session, request, idempotencyKey, context));
    }

    private ConfigViews.PublishResult publishNow(ConfigSession session, ConfigRequests.PublishConfig request,
                                                  String idempotencyKey, RequestContext context) {
        var root = rootForUpdate(session.systemId());
        var expectedRevision = ConfigErrors.version(request.draftRevision());
        var expectedRootVersion = ConfigErrors.version(request.configRootVersion());
        var checkId = ConfigErrors.id(request.checkId(), "checkId");
        if (root.revision() != expectedRevision || root.version() != expectedRootVersion) {
            throw ConfigErrors.versionConflict();
        }
        if (!"CHECKED".equals(root.status()) || !Objects.equals(root.lastCheckId(), checkId)) {
            throw ConfigErrors.conflict("CONFIG_CHECK_STALE", "发布前必须对当前草稿重新执行并通过检查");
        }
        var check = checkForUpdate(session.systemId(), checkId);
        if (!"PASSED".equals(check.status()) || check.revision() != root.revision()
                || !Objects.equals(check.baseVersionId(), root.baseVersionId())
                || check.expiresAt().isBefore(LocalDateTime.now())) {
            throw ConfigErrors.conflict("CONFIG_CHECK_STALE", "配置检查已失效，请重新检查");
        }
        var snapshot = snapshots.create(session.systemId());
        if (!snapshot.checksum().equals(check.checksum()) || !snapshot.checksum().equals(root.checksum())) {
            throw ConfigErrors.conflict("CONFIG_CHECK_STALE", "草稿内容与检查快照不一致");
        }
        if (snapshot.sizeBytes() > ConfigSnapshotService.MAX_SNAPSHOT_BYTES) {
            throw ConfigErrors.conflict("CONFIG_CHECK_FAILED", "配置快照超过 2 MiB 发布上限");
        }

        jdbc.update("UPDATE un_module_config_root SET status='PUBLISHING',updated_at=?,updated_by=? WHERE id=?",
                LocalDateTime.now(), session.accountId(), root.id());
        var sync = permissions.synchronizeSystem(session.systemId(), session.accountId(), "module.",
                snapshots.permissionDefinitions(session.systemId()));
        var now = LocalDateTime.now();
        var versionId = ids.nextId();
        var nextVersionNo = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no),0)+1 FROM un_module_config_version WHERE system_id=?",
                Long.class, session.systemId());
        var impact = impact(root.activeVersionId(), snapshot.node(), sync);
        var impactJson = write(impact);
        jdbc.update("INSERT INTO un_module_config_version (id,system_id,version_no,source_type,based_on_version_id,rollback_target_version_id,source_check_id,snapshot_json,snapshot_checksum,snapshot_size_bytes,impact_report_json,published_at,published_by,publish_reason) VALUES (?,?,?,'PUBLISH',?,NULL,?,?,?,?,?,?,?,?)",
                versionId, session.systemId(), nextVersionNo, root.baseVersionId(), checkId, snapshot.json(),
                snapshot.checksum(), snapshot.sizeBytes(), impactJson, now, session.accountId(), request.reason().trim());
        runtimeSchemas.project(session.systemId(), versionId, snapshot.node(), now);
        jdbc.update("INSERT INTO un_module_publish_record (id,system_id,operation_type,from_version_id,to_version_id,target_version_id,check_id,draft_revision,idempotency_key,request_id,trace_id,result,impact_report_json,operated_at,operated_by,reason) VALUES (?,?,'PUBLISH',?,?,NULL,?,?,?,?,?,'SUCCEEDED',?,?,?,?)",
                ids.nextId(), session.systemId(), root.activeVersionId(), versionId, checkId, root.revision(),
                idempotencyKey, context.requestId(), context.traceId(), impactJson, now, session.accountId(),
                request.reason().trim());
        lockPublishedCodes(session.systemId(), now);
        var changed = jdbc.update("UPDATE un_module_config_root SET status='CLEAN',active_version_id=?,base_version_id=?,draft_checksum=?,updated_at=?,updated_by=?,version=version+1 WHERE id=? AND version=? AND draft_revision=?",
                versionId, versionId, snapshot.checksum(), now, session.accountId(), root.id(), root.version(),
                root.revision());
        if (changed != 1) {
            throw ConfigErrors.conflict("CONFIG_PUBLISH_CONFLICT", "配置发布发生并发冲突");
        }
        recordPublication(session, versionId, nextVersionNo, request.reason(), snapshot.checksum(),
                sync.authzEpoch(), "MODULE_CONFIG_PUBLISHED", context);
        var version = versionView(versionId, session.systemId(), true);
        return new ConfigViews.PublishResult(version, revisions.summary(session.systemId()), sync.authzEpoch());
    }

    @Transactional
    public ConfigViews.PublishResult rollback(ConfigSession session, long targetVersionId,
                                              ConfigRequests.RollbackConfig request, String idempotencyKey,
                                              RequestContext context) {
        return mutations.idempotent(session.systemId() + ":config:rollback:" + targetVersionId,
                idempotencyKey, request, ConfigViews.PublishResult.class,
                () -> rollbackNow(session, targetVersionId, request, idempotencyKey, context));
    }

    private ConfigViews.PublishResult rollbackNow(ConfigSession session, long targetVersionId,
                                                   ConfigRequests.RollbackConfig request, String idempotencyKey,
                                                   RequestContext context) {
        var root = rootForUpdate(session.systemId());
        if (root.version() != ConfigErrors.version(request.configRootVersion())) {
            throw ConfigErrors.versionConflict();
        }
        if (!"CLEAN".equals(root.status()) || root.activeVersionId() == null) {
            throw ConfigErrors.conflict("CONFIG_DRAFT_DIRTY", "只能在草稿无未发布修改时回滚");
        }
        var current = snapshots.create(session.systemId());
        if (!Objects.equals(current.checksum(), root.checksum())) {
            throw ConfigErrors.conflict("CONFIG_DRAFT_DIRTY", "规范化草稿与当前发布版本不一致");
        }
        var target = storedSnapshot(session.systemId(), targetVersionId);
        var now = LocalDateTime.now();
        var nextRevision = root.revision() + 1;
        restoreSnapshot(session, target, nextRevision, now);
        var restored = snapshots.create(session.systemId());
        if (!restored.node().equals(target)) {
            throw ConfigErrors.conflict("CONFIG_PUBLISH_CONFLICT", "历史快照恢复后内容不一致");
        }

        var prepared = jdbc.update("UPDATE un_module_config_root SET status='DIRTY',draft_revision=?,"
                        + "draft_checksum=?,last_check_id=NULL,updated_at=?,updated_by=?,version=version+1 "
                        + "WHERE id=? AND version=?",
                nextRevision, restored.checksum(), now, session.accountId(), root.id(), root.version());
        if (prepared != 1) {
            throw ConfigErrors.conflict("CONFIG_PUBLISH_CONFLICT", "配置回滚准备阶段发生并发冲突");
        }
        var checkReport = checks.run(session, new ConfigRequests.RunCheck(Long.toString(nextRevision)));
        if (!"PASSED".equals(checkReport.status())) {
            throw ConfigErrors.conflict("CONFIG_CHECK_FAILED", "历史快照不再满足当前配置规则，回滚已取消");
        }
        var checkId = ConfigErrors.id(checkReport.id(), "checkId");
        var checkedRoot = rootForUpdate(session.systemId());
        var sync = permissions.synchronizeSystem(session.systemId(), session.accountId(), "module.",
                snapshots.permissionDefinitions(session.systemId()));
        var versionId = ids.nextId();
        var nextVersionNo = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no),0)+1 FROM un_module_config_version WHERE system_id=?",
                Long.class, session.systemId());
        var impact = impact(root.activeVersionId(), restored.node(), sync);
        var impactJson = write(impact);
        jdbc.update("INSERT INTO un_module_config_version (id,system_id,version_no,source_type,based_on_version_id,rollback_target_version_id,source_check_id,snapshot_json,snapshot_checksum,snapshot_size_bytes,impact_report_json,published_at,published_by,publish_reason) VALUES (?,?,?,'ROLLBACK',?,?,?,?,?,?,?,?,?,?)",
                versionId, session.systemId(), nextVersionNo, root.activeVersionId(), targetVersionId, checkId,
                restored.json(), restored.checksum(), restored.sizeBytes(), impactJson, now, session.accountId(),
                request.reason().trim());
        runtimeSchemas.project(session.systemId(), versionId, restored.node(), now);
        jdbc.update("INSERT INTO un_module_publish_record (id,system_id,operation_type,from_version_id,to_version_id,target_version_id,check_id,draft_revision,idempotency_key,request_id,trace_id,result,impact_report_json,operated_at,operated_by,reason) VALUES (?,?,'ROLLBACK',?,?,?,?,?,?,?,?,'SUCCEEDED',?,?,?,?)",
                ids.nextId(), session.systemId(), root.activeVersionId(), versionId, targetVersionId, checkId,
                nextRevision, idempotencyKey, context.requestId(), context.traceId(), impactJson, now,
                session.accountId(), request.reason().trim());
        lockPublishedCodes(session.systemId(), now);
        var changed = jdbc.update("UPDATE un_module_config_root SET status='CLEAN',draft_revision=?,draft_checksum=?,active_version_id=?,base_version_id=?,last_check_id=?,updated_at=?,updated_by=?,version=version+1 WHERE id=? AND version=?",
                nextRevision, restored.checksum(), versionId, versionId, checkId, now, session.accountId(), root.id(),
                checkedRoot.version());
        if (changed != 1) throw ConfigErrors.conflict("CONFIG_PUBLISH_CONFLICT", "配置回滚发生并发冲突");
        recordPublication(session, versionId, nextVersionNo, request.reason(), restored.checksum(),
                sync.authzEpoch(), "MODULE_CONFIG_ROLLED_BACK", context);
        return new ConfigViews.PublishResult(versionView(versionId, session.systemId(), true),
                revisions.summary(session.systemId()), sync.authzEpoch());
    }

    /**
     * Restores a historical snapshot into the draft only. The target version
     * remains immutable and the active runtime pointer does not move until the
     * regular check and publish workflow succeeds.
     */
    @Transactional
    public ConfigViews.RootSummary restoreDraft(
            ConfigSession session,
            long targetVersionId,
            ConfigRequests.RestoreConfig request,
            String idempotencyKey,
            RequestContext context
    ) {
        return mutations.idempotent(
                session.systemId() + ":config:restore-draft:"
                        + targetVersionId,
                idempotencyKey, request, ConfigViews.RootSummary.class,
                () -> restoreDraftNow(
                        session, targetVersionId, request, context));
    }

    private ConfigViews.RootSummary restoreDraftNow(
            ConfigSession session,
            long targetVersionId,
            ConfigRequests.RestoreConfig request,
            RequestContext context
    ) {
        var root = rootForUpdate(session.systemId());
        if (root.version()
                != ConfigErrors.version(request.expectedVersion())) {
            throw ConfigErrors.versionConflict();
        }
        if (!"CLEAN".equals(root.status()) || root.activeVersionId() == null) {
            throw ConfigErrors.conflict(
                    "CONFIG_DRAFT_DIRTY",
                    "恢复历史版本前必须先处理当前未发布草稿");
        }
        var current = snapshots.create(session.systemId());
        if (!Objects.equals(current.checksum(), root.checksum())) {
            throw ConfigErrors.conflict(
                    "CONFIG_DRAFT_DIRTY", "当前草稿与已发布版本不一致");
        }
        var target = storedSnapshot(session.systemId(), targetVersionId);
        var now = LocalDateTime.now();
        var nextRevision = root.revision() + 1;
        restoreSnapshot(session, target, nextRevision, now);
        var restored = snapshots.create(session.systemId());
        if (!restored.node().equals(target)) {
            throw ConfigErrors.conflict(
                    "CONFIG_RESTORE_CONFLICT", "历史快照恢复后内容不一致");
        }
        var changed = jdbc.update(
                "UPDATE un_module_config_root SET status='DIRTY',"
                        + "draft_revision=?,draft_checksum=?,"
                        + "last_check_id=NULL,updated_at=?,updated_by=?,"
                        + "version=version+1 WHERE id=? AND version=?",
                nextRevision, restored.checksum(), now, session.accountId(),
                root.id(), root.version());
        if (changed != 1) {
            throw ConfigErrors.conflict(
                    "CONFIG_RESTORE_CONFLICT", "配置恢复发生并发冲突");
        }
        mutations.changed(
                session, "CONFIG_VERSION", Long.toString(targetVersionId),
                "MODULE_CONFIG_VERSION_RESTORED_TO_DRAFT",
                Map.of("activeVersionId",
                        Long.toString(root.activeVersionId()),
                        "draftRevision", Long.toString(root.revision())),
                Map.of("activeVersionId",
                        Long.toString(root.activeVersionId()),
                        "draftRevision", Long.toString(nextRevision),
                        "reason", request.reason().trim()),
                nextRevision, "restore-draft:" + targetVersionId, context);
        return revisions.summary(session.systemId());
    }

    public List<ConfigViews.ConfigVersion> versions(long systemId) {
        var active = activeVersionId(systemId);
        return jdbc.query("SELECT * FROM un_module_config_version WHERE system_id=? ORDER BY version_no DESC",
                (rs, n) -> mapVersion(rs, Objects.equals(active, rs.getLong("id"))), systemId);
    }

    public ConfigViews.VersionDiff diff(long systemId, long fromId, long toId) {
        var from = snapshotNode(systemId, fromId);
        var to = snapshotNode(systemId, toId);
        var changes = objectMapper.createObjectNode();
        for (var section : List.of("groups", "modules", "dictionaries", "dictionaryItems", "fields", "pages",
                "components", "actions", "rules", "permissions")) {
            changes.set(section, sectionDiff(from.path(section), to.path(section)));
        }
        return new ConfigViews.VersionDiff(Long.toString(fromId), Long.toString(toId), changes);
    }

    private ObjectNode sectionDiff(JsonNode from, JsonNode to) {
        var before = byIdentity(from);
        var after = byIdentity(to);
        var added = objectMapper.createArrayNode();
        var removed = objectMapper.createArrayNode();
        var changed = objectMapper.createArrayNode();
        after.forEach((id, value) -> {
            if (!before.containsKey(id)) added.add(id);
            else if (!value.equals(before.get(id))) changed.add(id);
        });
        before.keySet().stream().filter(id -> !after.containsKey(id)).forEach(removed::add);
        var result = objectMapper.createObjectNode();
        result.set("added", added);result.set("removed", removed);result.set("changed", changed);
        return result;
    }

    private Map<String, JsonNode> byIdentity(JsonNode array) {
        var result = new LinkedHashMap<String, JsonNode>();
        var index = 0;
        for (var item : array) {
            var id = item.hasNonNull("id") ? item.path("id").asText() : Integer.toString(index);
            result.put(id, item);index++;
        }
        return result;
    }

    private JsonNode snapshotNode(long systemId, long versionId) {
        return storedSnapshot(systemId, versionId);
    }

    private JsonNode storedSnapshot(long systemId, long versionId) {
        var rows = jdbc.queryForList("SELECT snapshot_json FROM un_module_config_version WHERE system_id=? AND id=?",
                systemId, versionId);
        if (rows.isEmpty()) throw ConfigErrors.notFound();
        try {
            return objectMapper.readTree(String.valueOf(rows.getFirst().get("snapshot_json")));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored configuration snapshot is invalid", exception);
        }
    }

    private ConfigViews.ConfigVersion versionView(long id, long systemId, boolean active) {
        var rows = jdbc.query("SELECT * FROM un_module_config_version WHERE system_id=? AND id=?",
                (rs, n) -> mapVersion(rs, active), systemId, id);
        if (rows.isEmpty()) throw ConfigErrors.notFound();
        return rows.getFirst();
    }

    private static ConfigViews.ConfigVersion mapVersion(java.sql.ResultSet rs, boolean active)
            throws java.sql.SQLException {
        return new ConfigViews.ConfigVersion(Long.toString(rs.getLong("id")), Long.toString(rs.getLong("version_no")),
                rs.getString("source_type"), nullableString(rs, "based_on_version_id"),
                nullableString(rs, "rollback_target_version_id"), rs.getString("snapshot_checksum"),
                rs.getLong("snapshot_size_bytes"), rs.getTimestamp("published_at").toLocalDateTime().toString(),
                Long.toString(rs.getLong("published_by")), rs.getString("publish_reason"), active);
    }

    private Root rootForUpdate(long systemId) {
        var rows = jdbc.query("SELECT * FROM un_module_config_root WHERE system_id=? FOR UPDATE", (rs, n) -> new Root(
                rs.getLong("id"), rs.getString("status"), rs.getLong("draft_revision"),
                nullableLong(rs, "active_version_id"), nullableLong(rs, "base_version_id"),
                nullableLong(rs, "last_check_id"), rs.getString("draft_checksum"), rs.getLong("version")
        ), systemId);
        if (rows.isEmpty()) throw ConfigErrors.conflict("CONFIG_CHECK_STALE", "配置尚未检查");
        return rows.getFirst();
    }

    private Check checkForUpdate(long systemId, long id) {
        var rows = jdbc.query("SELECT * FROM un_module_config_check WHERE system_id=? AND id=? FOR UPDATE",
                (rs, n) -> new Check(rs.getString("status"), rs.getLong("draft_revision"),
                        nullableLong(rs, "base_version_id"), rs.getString("draft_checksum"),
                        rs.getTimestamp("expires_at").toLocalDateTime()), systemId, id);
        if (rows.isEmpty()) throw ConfigErrors.notFound();
        return rows.getFirst();
    }

    private Map<String, Object> impact(Long fromVersionId, JsonNode snapshot,
                                       DynamicPermissionFacade.SyncResult sync) {
        return Map.of(
                "fromVersionId", fromVersionId == null ? "" : Long.toString(fromVersionId),
                "resourceCounts", Map.of(
                        "groups", snapshot.path("groups").size(), "modules", snapshot.path("modules").size(),
                        "fields", snapshot.path("fields").size(), "pages", snapshot.path("pages").size(),
                        "actions", snapshot.path("actions").size(), "rules", snapshot.path("rules").size()
                ),
                "permissionCount", sync.permissionIds().size(),
                "authzEpoch", sync.authzEpoch()
        );
    }

    private void lockPublishedCodes(long systemId, LocalDateTime now) {
        for (var table : List.of("un_module_group", "un_module_definition", "un_module_dictionary",
                "un_module_dictionary_item", "un_module_field", "un_module_page", "un_module_action",
                "un_module_rule")) {
            jdbc.update("UPDATE " + table + " SET code_locked_at=COALESCE(code_locked_at,?) WHERE system_id=? AND deleted_at IS NULL",
                    now, systemId);
        }
    }

    private void restoreSnapshot(ConfigSession session, JsonNode snapshot, long revision, LocalDateTime now) {
        for (var table : List.of("un_module_config_reference", "un_module_dictionary_item_closure",
                "un_module_page_component", "un_module_rule", "un_module_permission", "un_module_action",
                "un_module_page", "un_module_field", "un_module_dictionary_item", "un_module_dictionary",
                "un_module_definition", "un_module_group")) {
            jdbc.update("DELETE FROM " + table + " WHERE system_id=?", session.systemId());
        }
        for (var n : snapshot.path("groups")) {
            jdbc.update("INSERT INTO un_module_group (id,system_id,group_code,group_name,description,icon_key,sort_order,desired_status,code_locked_at,created_revision,updated_revision,created_at,created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                    id(n,"id"),session.systemId(),text(n,"group_code"),text(n,"group_name"),nullableText(n,"description"),nullableText(n,"icon_key"),integer(n,"sort_order"),text(n,"desired_status"),now,revision,revision,now,session.accountId(),now,session.accountId());
        }
        for (var n : snapshot.path("modules")) {
            jdbc.update("INSERT INTO un_module_definition (id,system_id,group_id,module_code,module_name,description,icon_key,sort_order,desired_status,allow_comments,allow_team,code_locked_at,created_revision,updated_revision,created_at,created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                    id(n,"id"),session.systemId(),id(n,"group_id"),text(n,"module_code"),text(n,"module_name"),nullableText(n,"description"),nullableText(n,"icon_key"),integer(n,"sort_order"),text(n,"desired_status"),flag(n,"allow_comments"),flag(n,"allow_team"),now,revision,revision,now,session.accountId(),now,session.accountId());
        }
        for (var n : snapshot.path("dictionaries")) {
            jdbc.update("INSERT INTO un_module_dictionary (id,system_id,dictionary_code,dictionary_name,dictionary_type,category,description,desired_status,code_locked_at,created_revision,updated_revision,created_at,created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                    id(n,"id"),session.systemId(),text(n,"dictionary_code"),text(n,"dictionary_name"),text(n,"dictionary_type"),nullableText(n,"category"),nullableText(n,"description"),text(n,"desired_status"),now,revision,revision,now,session.accountId(),now,session.accountId());
        }
        for (var n : snapshot.path("dictionaryItems")) {
            jdbc.update("INSERT INTO un_module_dictionary_item (id,system_id,dictionary_id,parent_id,item_code,item_label,semantic_key,color_value,icon_key,sort_order,depth_level,depth_path,is_default,desired_status,code_locked_at,created_revision,updated_revision,created_at,created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                    id(n,"id"),session.systemId(),id(n,"dictionary_id"),nullableId(n,"parent_id"),text(n,"item_code"),text(n,"item_label"),nullableText(n,"semantic_key"),nullableText(n,"color_value"),nullableText(n,"icon_key"),integer(n,"sort_order"),integer(n,"depth_level"),text(n,"depth_path"),flag(n,"is_default"),text(n,"desired_status"),now,revision,revision,now,session.accountId(),now,session.accountId());
        }
        for (var n : snapshot.path("dictionaryClosure")) {
            jdbc.update("INSERT INTO un_module_dictionary_item_closure (id,system_id,dictionary_id,ancestor_id,descendant_id,depth,created_at,created_by) VALUES (?,?,?,?,?,?,?,?)",
                    ids.nextId(),session.systemId(),id(n,"dictionary_id"),id(n,"ancestor_id"),id(n,"descendant_id"),
                    integer(n,"depth"),now,session.accountId());
        }
        for (var n : snapshot.path("fields")) {
            jdbc.update("INSERT INTO un_module_field (id,system_id,module_id,dictionary_id,target_module_id,field_code,field_name,field_type,sort_order,is_required,is_hidden,is_readonly,is_searchable,is_filterable,show_in_list,show_in_detail,index_mode,desired_status,property_json,code_locked_at,created_revision,updated_revision,created_at,created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                    id(n,"id"),session.systemId(),id(n,"module_id"),nullableId(n,"dictionary_id"),nullableId(n,"target_module_id"),text(n,"field_code"),text(n,"field_name"),text(n,"field_type"),integer(n,"sort_order"),flag(n,"is_required"),flag(n,"is_hidden"),flag(n,"is_readonly"),flag(n,"is_searchable"),flag(n,"is_filterable"),flag(n,"show_in_list"),flag(n,"show_in_detail"),text(n,"index_mode"),text(n,"desired_status"),json(n,"property_json"),now,revision,revision,now,session.accountId(),now,session.accountId());
        }
        for (var n : snapshot.path("pages")) {
            jdbc.update("INSERT INTO un_module_page (id,system_id,module_id,page_code,page_name,page_type,is_default,desired_status,layout_json,code_locked_at,created_revision,updated_revision,created_at,created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                    id(n,"id"),session.systemId(),id(n,"module_id"),text(n,"page_code"),text(n,"page_name"),text(n,"page_type"),flag(n,"is_default"),text(n,"desired_status"),json(n,"layout_json"),now,revision,revision,now,session.accountId(),now,session.accountId());
        }
        for (var n : snapshot.path("components")) {
            jdbc.update("INSERT INTO un_module_page_component (id,system_id,page_id,parent_component_id,field_id,component_key,component_type,sort_order,grid_row,grid_column,grid_span,property_json,created_revision,updated_revision,created_at,created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                    id(n,"id"),session.systemId(),id(n,"page_id"),nullableId(n,"parent_component_id"),nullableId(n,"field_id"),text(n,"component_key"),text(n,"component_type"),integer(n,"sort_order"),integer(n,"grid_row"),integer(n,"grid_column"),integer(n,"grid_span"),json(n,"property_json"),revision,revision,now,session.accountId(),now,session.accountId());
        }
        for (var n : snapshot.path("actions")) {
            jdbc.update("INSERT INTO un_module_action (id,system_id,module_id,action_code,action_name,action_type,placement,permission_code,confirm_message,sort_order,desired_status,property_json,code_locked_at,created_revision,updated_revision,created_at,created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                    id(n,"id"),session.systemId(),id(n,"module_id"),text(n,"action_code"),text(n,"action_name"),text(n,"action_type"),text(n,"placement"),text(n,"permission_code"),nullableText(n,"confirm_message"),integer(n,"sort_order"),text(n,"desired_status"),json(n,"property_json"),now,revision,revision,now,session.accountId(),now,session.accountId());
        }
        for (var n : snapshot.path("rules")) {
            jdbc.update("INSERT INTO un_module_rule (id,system_id,module_id,rule_code,rule_name,rule_type,priority,condition_json,effect_json,desired_status,code_locked_at,created_revision,updated_revision,created_at,created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                    id(n,"id"),session.systemId(),id(n,"module_id"),text(n,"rule_code"),text(n,"rule_name"),text(n,"rule_type"),integer(n,"priority"),json(n,"condition_json"),json(n,"effect_json"),text(n,"desired_status"),now,revision,revision,now,session.accountId(),now,session.accountId());
        }
        for (var n : snapshot.path("permissions")) {
            jdbc.update("INSERT INTO un_module_permission (id,system_id,module_id,resource_type,resource_id,permission_code,permission_name,permission_type,desired_status,created_revision,updated_revision,created_at,created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                    id(n,"id"),session.systemId(),id(n,"module_id"),text(n,"resource_type"),id(n,"resource_id"),text(n,"permission_code"),text(n,"permission_name"),text(n,"permission_type"),text(n,"desired_status"),revision,revision,now,session.accountId(),now,session.accountId());
        }
        for (var n : snapshot.path("references")) {
            jdbc.update("INSERT INTO un_module_config_reference (id,system_id,source_type,source_id,target_type,target_id,relation_type,property_path,created_revision,created_at,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    ids.nextId(),session.systemId(),text(n,"source_type"),id(n,"source_id"),text(n,"target_type"),id(n,"target_id"),text(n,"relation_type"),text(n,"property_path"),revision,now,session.accountId());
        }
    }

    private static long id(JsonNode node,String field){return Long.parseLong(node.path(field).asText());}
    private static Long nullableId(JsonNode node,String field){var value=node.get(field);return value==null||value.isNull()?null:Long.parseLong(value.asText());}
    private static String text(JsonNode node,String field){return node.path(field).asText();}
    private static String nullableText(JsonNode node,String field){var value=node.get(field);return value==null||value.isNull()?null:value.asText();}
    private static int integer(JsonNode node,String field){return node.path(field).asInt();}
    private static boolean flag(JsonNode node,String field){var value=node.path(field);return value.isBoolean()?value.asBoolean():value.asInt()!=0;}
    private static String json(JsonNode node,String field){return node.path(field).toString();}

    private void recordPublication(ConfigSession session, long versionId, long versionNo, String reason,
                                   String checksum, long authzEpoch, String action, RequestContext context) {
        var aggregate = new AggregateRef("CONFIG_VERSION", Long.toString(versionId));
        audit.recordSuccess(OperationAudit.success(new OperationAudit.Actor(session.accountId(), "WEB"),
                new OperationAudit.Context(ContextType.SYSTEM, session.systemId(), session.tenantId()), aggregate,
                action, null, Map.of("versionNo", Long.toString(versionNo), "reason", reason),
                context.requestId(), context.traceId()));
        outbox.enqueue(new OutboxEvent(action, 1, aggregate,
                new OutboxEvent.Context(session.systemId(), session.tenantId()),
                "module-config-published:" + session.systemId() + ":" + versionId,
                Map.of("systemId", Long.toString(session.systemId()), "versionId", Long.toString(versionId),
                        "versionNo", Long.toString(versionNo), "checksum", checksum,
                        "authzEpoch", Long.toString(authzEpoch)), context.traceId()));
    }

    private Long activeVersionId(long systemId) {
        var values = jdbc.query("SELECT active_version_id FROM un_module_config_root WHERE system_id=?",
                rs -> rs.next() ? nullableLong(rs, "active_version_id") : null, systemId);
        return values;
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize impact report", exception); }
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        var value = rs.getLong(column);return rs.wasNull() ? null : value;
    }

    private static String nullableString(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        var value = nullableLong(rs, column);return value == null ? null : Long.toString(value);
    }

    private record Root(long id, String status, long revision, Long activeVersionId, Long baseVersionId,
                        Long lastCheckId, String checksum, long version) { }
    private record Check(String status, long revision, Long baseVersionId, String checksum,
                         LocalDateTime expiresAt) { }
}
