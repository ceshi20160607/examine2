package com.unique.examine.module.manage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.manage.api.ConfigRequests;
import com.unique.examine.module.manage.api.ConfigViews;
import com.unique.examine.module.manage.api.FieldPermissionCodes;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.runtime.security.SensitiveKeyProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ConfigCheckService {
    private static final int CHECK_TTL_MINUTES = 10;

    private final JdbcTemplate jdbc;
    private final IdService ids;
    private final ConfigSnapshotService snapshots;
    private final ObjectMapper objectMapper;
    private final SensitiveKeyProvider sensitiveKeys;
    private final DerivedFieldContractService derivedFields;
    private final ConfigFilterScenarioValidator filterScenarios;

    public ConfigCheckService(JdbcTemplate jdbc, IdService ids, ConfigSnapshotService snapshots,
                              ObjectMapper objectMapper, SensitiveKeyProvider sensitiveKeys,
                              DerivedFieldContractService derivedFields) {
        this.jdbc = jdbc;
        this.ids = ids;
        this.snapshots = snapshots;
        this.objectMapper = objectMapper;
        this.sensitiveKeys = sensitiveKeys;
        this.derivedFields = derivedFields;
        this.filterScenarios = new ConfigFilterScenarioValidator(objectMapper);
    }

    @Transactional
    public ConfigViews.CheckReport run(ConfigSession session, ConfigRequests.RunCheck request) {
        ensureRoot(session);
        var root = jdbc.queryForMap(
                "SELECT id,draft_revision,base_version_id,version FROM un_module_config_root WHERE system_id=? FOR UPDATE",
                session.systemId()
        );
        var revision = ((Number) root.get("draft_revision")).longValue();
        if (revision != ConfigErrors.version(request.draftRevision())) {
            throw ConfigErrors.versionConflict();
        }
        jdbc.update("UPDATE un_module_config_root SET status='CHECKING',updated_at=?,updated_by=?,version=version+1 WHERE id=?",
                LocalDateTime.now(), session.accountId(), root.get("id"));

        var snapshot = snapshots.create(session.systemId());
        var issues = inspect(session.systemId(), revision, snapshot);
        var blockers = (int) issues.stream().filter(issue -> "BLOCKER".equals(issue.severity())).count();
        var warnings = issues.size() - blockers;
        var status = blockers == 0 ? "PASSED" : "FAILED";
        var now = LocalDateTime.now();
        var checkId = ids.nextId();
        var reportJson = write(Map.of(
                "blockerCount", blockers,
                "warningCount", warnings,
                "snapshotChecksum", snapshot.checksum(),
                "snapshotSizeBytes", snapshot.sizeBytes()
        ));
        jdbc.update("INSERT INTO un_module_config_check (id,system_id,base_version_id,draft_revision,draft_checksum,status,blocker_count,warning_count,snapshot_size_bytes,report_json,started_at,completed_at,expires_at,checked_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                checkId, session.systemId(), root.get("base_version_id"), revision, snapshot.checksum(), status,
                blockers, warnings, snapshot.sizeBytes(), reportJson, now, now, now.plusMinutes(CHECK_TTL_MINUTES),
                session.accountId());
        for (var issue : issues) {
            var issueId = ids.nextId();
            jdbc.update("INSERT INTO un_module_config_check_issue (id,system_id,check_id,severity,issue_code,resource_type,resource_id,property_path,issue_message,suggested_action,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    issueId, session.systemId(), checkId, issue.severity(), issue.code(), issue.resourceType(),
                    issue.resourceId(), issue.propertyPath(), issue.message(), issue.suggestedAction(), now);
            issue.id = issueId;
        }
        jdbc.update("UPDATE un_module_config_root SET status=?,draft_checksum=?,last_check_id=?,updated_at=?,updated_by=?,version=version+1 WHERE id=? AND draft_revision=?",
                blockers == 0 ? "CHECKED" : "CHECK_FAILED", snapshot.checksum(), checkId, now,
                session.accountId(), root.get("id"), revision);
        return view(checkId, status, blockers, warnings, revision, snapshot, now.plusMinutes(CHECK_TTL_MINUTES), issues);
    }

    public ConfigViews.CheckReport get(long systemId, long checkId) {
        var rows = jdbc.queryForList("SELECT * FROM un_module_config_check WHERE system_id=? AND id=?", systemId, checkId);
        if (rows.isEmpty()) {
            throw ConfigErrors.notFound();
        }
        var row = rows.getFirst();
        var issues = jdbc.query("SELECT * FROM un_module_config_check_issue WHERE system_id=? AND check_id=? ORDER BY severity,property_path,id",
                (rs, n) -> new ConfigViews.CheckIssue(
                        Long.toString(rs.getLong("id")), rs.getString("severity"), rs.getString("issue_code"),
                        rs.getString("resource_type"), nullableId(rs.getObject("resource_id")),
                        rs.getString("property_path"), rs.getString("issue_message"),
                        rs.getString("suggested_action")
                ), systemId, checkId);
        return new ConfigViews.CheckReport(
                Long.toString(checkId), String.valueOf(row.get("status")), ((Number) row.get("blocker_count")).intValue(),
                ((Number) row.get("warning_count")).intValue(), String.valueOf(row.get("draft_revision")),
                String.valueOf(row.get("draft_checksum")), ((Number) row.get("snapshot_size_bytes")).longValue(),
                String.valueOf(row.get("expires_at")), issues, String.valueOf(row.get("version"))
        );
    }

    private List<Issue> inspect(long systemId, long draftRevision, ConfigSnapshotService.Snapshot snapshot) {
        var issues = new ArrayList<Issue>();
        if (snapshot.sizeBytes() > ConfigSnapshotService.MAX_SNAPSHOT_BYTES) {
            issues.add(Issue.blocker("SNAPSHOT_TOO_LARGE", "ROOT", null, "snapshot",
                    "配置快照超过 2 MiB 发布上限", "删除无用配置或缩小结构化属性"));
        }
        var enabledModules = count("un_module_definition", "desired_status='ENABLED'", systemId);
        if (enabledModules == 0) {
            issues.add(Issue.blocker("NO_ENABLED_MODULE", "ROOT", null, "modules",
                    "至少需要一个启用模块", "创建或启用一个模块"));
        }
        for (var table : List.of("un_module_group", "un_module_definition", "un_module_dictionary",
                "un_module_dictionary_item", "un_module_field", "un_module_page", "un_module_page_component",
                "un_module_action", "un_module_rule", "un_module_permission")) {
            var invalid = jdbc.queryForObject("SELECT COUNT(*) FROM " + table
                    + " WHERE system_id=? AND deleted_at IS NULL AND (created_revision>updated_revision OR updated_revision>?)",
                    Long.class, systemId, draftRevision);
            if (invalid != null && invalid > 0) {
                issues.add(Issue.blocker("DRAFT_REVISION_OUT_OF_RANGE", "ROOT", null, table,
                        "资源修订号超出当前草稿修订号", "修复异常资源修订数据后重新检查"));
            }
        }
        jdbc.query("SELECT id FROM un_module_field WHERE system_id=? AND deleted_at IS NULL AND desired_status='ENABLED' AND is_hidden=1 AND is_required=1",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> issues.add(Issue.blocker(
                        "FIELD_HIDDEN_REQUIRED_CONFLICT", "FIELD", rs.getLong("id"), "required",
                        "字段不能同时隐藏且必填", "取消隐藏或取消必填")), systemId);
        jdbc.query("SELECT id FROM un_module_dictionary_item WHERE system_id=? AND deleted_at IS NULL AND is_default=1 AND desired_status<>'ENABLED'",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> issues.add(Issue.blocker(
                        "DICTIONARY_DEFAULT_INVALID", "DICTIONARY_ITEM", rs.getLong("id"), "isDefault",
                        "默认字典项必须处于启用状态", "启用该字典项或取消默认")), systemId);
        jdbc.query("SELECT c.id FROM un_module_page_component c JOIN un_module_page_component p ON p.system_id=c.system_id AND p.id=c.parent_component_id WHERE c.system_id=? AND c.deleted_at IS NULL AND (p.deleted_at IS NOT NULL OR p.page_id<>c.page_id)",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> issues.add(Issue.blocker(
                        "COMPONENT_TREE_INVALID", "COMPONENT", rs.getLong("id"), "parentComponentId",
                        "组件父级不在同一页面", "重新选择同页父组件")), systemId);
        jdbc.query("SELECT m.id,m.module_code FROM un_module_definition m JOIN un_module_group g ON g.system_id=m.system_id AND g.id=m.group_id WHERE m.system_id=? AND m.deleted_at IS NULL AND m.desired_status='ENABLED' AND (g.deleted_at IS NOT NULL OR g.desired_status<>'ENABLED')",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    issues.add(Issue.blocker("MODULE_GROUP_NOT_ENABLED", "MODULE", rs.getLong("id"), "groupId",
                            "启用模块必须位于启用模块组", "启用模块组或停用该模块"));
                }, systemId);
        jdbc.query("SELECT m.id,m.module_code,t.page_type,COUNT(p.id) page_count FROM un_module_definition m CROSS JOIN (SELECT 'LIST' page_type UNION ALL SELECT 'FORM' UNION ALL SELECT 'DETAIL') t LEFT JOIN un_module_page p ON p.system_id=m.system_id AND p.module_id=m.id AND p.page_type=t.page_type AND p.is_default=1 AND p.desired_status='ENABLED' AND p.deleted_at IS NULL WHERE m.system_id=? AND m.deleted_at IS NULL AND m.desired_status='ENABLED' GROUP BY m.id,m.module_code,t.page_type HAVING COUNT(p.id)<>1",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> issues.add(Issue.blocker("DEFAULT_PAGE_MISSING", "MODULE", rs.getLong("id"),
                        "pages." + rs.getString("page_type"), "启用模块必须有且仅有一个启用的默认 "
                                + rs.getString("page_type") + " 页面", "设置对应默认页面")), systemId);
        jdbc.query("SELECT f.id FROM un_module_field f JOIN un_module_dictionary d ON d.system_id=f.system_id AND d.id=f.dictionary_id WHERE f.system_id=? AND f.deleted_at IS NULL AND f.desired_status='ENABLED' AND (d.deleted_at IS NOT NULL OR d.desired_status<>'ENABLED')",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> issues.add(Issue.blocker("FIELD_DICTIONARY_NOT_ENABLED", "FIELD", rs.getLong("id"),
                        "dictionaryId", "启用字段引用的字典未启用", "启用字典或停用字段")), systemId);
        jdbc.query("SELECT d.id FROM un_module_dictionary d LEFT JOIN un_module_dictionary_item i ON i.system_id=d.system_id AND i.dictionary_id=d.id AND i.deleted_at IS NULL AND i.desired_status='ENABLED' WHERE d.system_id=? AND d.deleted_at IS NULL AND d.desired_status='ENABLED' GROUP BY d.id HAVING COUNT(i.id)=0",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> issues.add(Issue.warning("DICTIONARY_HAS_NO_ENABLED_ITEM", "DICTIONARY", rs.getLong("id"),
                        "items", "启用字典没有可用字典项", "至少添加一个启用字典项")), systemId);
        jdbc.query("SELECT m.id,COUNT(p.id) permission_count FROM un_module_definition m LEFT JOIN un_module_permission p ON p.system_id=m.system_id AND p.module_id=m.id AND p.resource_type='MODULE' AND p.desired_status='ENABLED' AND p.deleted_at IS NULL WHERE m.system_id=? AND m.deleted_at IS NULL AND m.desired_status='ENABLED' GROUP BY m.id HAVING COUNT(p.id)<4",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> issues.add(Issue.blocker("MODULE_BASE_PERMISSIONS_MISSING", "MODULE", rs.getLong("id"),
                        "permissions", "启用模块缺少基础 view/create/update/delete 权限", "修复模块权限映射")), systemId);
        jdbc.query("SELECT a.id FROM un_module_action a LEFT JOIN un_module_permission p ON p.system_id=a.system_id AND p.module_id=a.module_id AND p.resource_type='ACTION' AND p.resource_id=a.id AND p.permission_code=a.permission_code AND p.desired_status='ENABLED' AND p.deleted_at IS NULL WHERE a.system_id=? AND a.deleted_at IS NULL AND a.desired_status='ENABLED' AND p.id IS NULL",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> issues.add(Issue.blocker(
                        "PERMISSION_MAPPING_INVALID", "ACTION", rs.getLong("id"), "permissionCode",
                        "启用动作缺少对应权限映射", "修复动作权限映射")), systemId);
        jdbc.query("SELECT p.id,p.page_type FROM un_module_page p LEFT JOIN un_module_page_component c ON c.system_id=p.system_id AND c.page_id=p.id AND c.deleted_at IS NULL WHERE p.system_id=? AND p.deleted_at IS NULL AND p.desired_status='ENABLED' AND p.page_type IN ('FORM','DETAIL') GROUP BY p.id,p.page_type HAVING COUNT(c.id)=0",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> issues.add(Issue.warning("PAGE_HAS_NO_COMPONENT", "PAGE", rs.getLong("id"), "components",
                        rs.getString("page_type") + " 页面没有组件", "添加页面组件")), systemId);
        if (!sensitiveKeys.available()) {
            jdbc.query("SELECT id FROM un_module_field WHERE system_id=? AND deleted_at IS NULL "
                            + "AND desired_status='ENABLED' AND field_type IN ('IDENTITY','SECRET')",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> issues.add(Issue.blocker(
                            "SENSITIVE_KEY_UNAVAILABLE", "FIELD", rs.getLong("id"), "fieldType",
                            "Sensitive fields require an external encryption and HMAC key ring",
                            "Configure EXAMINE_SENSITIVE_KEY_RING_FILE and rerun the check")), systemId);
        }
        inspectP4C3Fields(systemId, issues);
        inspectDerivedFields(snapshot.node(), issues);
        inspectStatusFields(systemId, issues);
        inspectFilterScenarios(snapshot.node(), issues);
        inspectFieldPermissionStaging(systemId, snapshot.node(), issues);
        return issues;
    }

    private void inspectFieldPermissionStaging(long systemId, JsonNode draft, List<Issue> issues) {
        var activeRows = jdbc.query(
                "SELECT v.snapshot_json FROM un_module_config_root r JOIN un_module_config_version v "
                        + "ON v.system_id=r.system_id AND v.id=r.active_version_id WHERE r.system_id=?",
                (row, number) -> row.getString("snapshot_json"), systemId);
        JsonNode active = null;
        if (!activeRows.isEmpty()) {
            try {
                active = objectMapper.readTree(activeRows.getFirst());
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Published configuration snapshot is invalid", exception);
            }
        }
        for (var finding : unstagedEnforcedPermissions(draft, active)) {
            issues.add(Issue.blocker(
                    "FIELD_PERMISSION_NOT_STAGED", "FIELD", finding.fieldId(),
                    finding.direction() + "PermissionMode",
                    "Field " + finding.direction() + " permission must be published as STAGED before ENFORCED",
                    "Publish the permission as STAGED, grant it through a role, then enforce it"));
        }
    }

    static List<FieldPermissionStageFinding> unstagedEnforcedPermissions(JsonNode draft, JsonNode active) {
        var activeDeclarations = genericFieldPermissions(active, Set.of("DISABLED", "ENABLED"));
        return genericFieldPermissions(draft, Set.of("ENABLED")).values().stream()
                .filter(declaration -> !activeDeclarations.containsKey(declaration.code()))
                .map(declaration -> new FieldPermissionStageFinding(
                        declaration.fieldId(), declaration.direction(), declaration.code()))
                .toList();
    }

    private static Map<String, GenericFieldPermission> genericFieldPermissions(
            JsonNode snapshot, Set<String> statuses) {
        if (snapshot == null || snapshot.isMissingNode() || snapshot.isNull()) return Map.of();
        var moduleCodes = new java.util.HashMap<String, String>();
        snapshot.path("modules").forEach(module -> moduleCodes.put(
                module.path("id").asText(), module.path("module_code").asText()));
        var fields = new java.util.HashMap<String, JsonNode>();
        snapshot.path("fields").forEach(field -> fields.put(field.path("id").asText(), field));
        var result = new java.util.LinkedHashMap<String, GenericFieldPermission>();
        snapshot.path("permissions").forEach(permission -> {
            if (!"FIELD".equals(permission.path("resource_type").asText())
                    || !statuses.contains(permission.path("desired_status").asText())) return;
            var field = fields.get(permission.path("resource_id").asText());
            if (field == null) return;
            var moduleCode = moduleCodes.get(field.path("module_id").asText());
            if (moduleCode == null) return;
            var fieldId = Long.parseLong(field.path("id").asText());
            var fieldCode = field.path("field_code").asText();
            var code = permission.path("permission_code").asText();
            if (FieldPermissionCodes.read(moduleCode, fieldCode).equals(code)) {
                result.put(code, new GenericFieldPermission(fieldId, "read", code));
            } else if (FieldPermissionCodes.write(moduleCode, fieldCode).equals(code)) {
                result.put(code, new GenericFieldPermission(fieldId, "write", code));
            }
        });
        return result;
    }

    private void inspectFilterScenarios(
            com.fasterxml.jackson.databind.JsonNode snapshot,
            List<Issue> issues
    ) {
        for (var finding : filterScenarios.inspect(snapshot)) {
            issues.add(Issue.blocker(
                    finding.code(), "PAGE", finding.pageId(), finding.propertyPath(),
                    finding.message(), finding.suggestedAction()));
        }
    }

    private void inspectDerivedFields(com.fasterxml.jackson.databind.JsonNode snapshot, List<Issue> issues) {
        for (var finding : derivedFields.analyze(snapshot).issues()) {
            issues.add(Issue.blocker(finding.code(), "FIELD", finding.fieldId(), finding.propertyPath(),
                    finding.message(), finding.suggestedAction()));
        }
    }

    private void inspectP4C3Fields(long systemId, List<Issue> issues) {
        jdbc.query("SELECT f.id FROM un_module_field f LEFT JOIN un_module_definition m "
                        + "ON m.system_id=f.system_id AND m.id=f.target_module_id AND m.deleted_at IS NULL "
                        + "WHERE f.system_id=? AND f.deleted_at IS NULL AND f.desired_status='ENABLED' "
                        + "AND f.field_type IN ('RELATION','SUBTABLE') "
                        + "AND (m.id IS NULL OR m.desired_status<>'ENABLED')",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> issues.add(Issue.blocker(
                        "RELATION_TARGET_MODULE_INVALID", "FIELD", row.getLong("id"), "targetModuleId",
                        "RELATION and SUBTABLE require an enabled target module",
                        "Select an enabled module or disable the field")), systemId);

        jdbc.query("SELECT f.id FROM un_module_field f "
                        + "LEFT JOIN un_module_field source ON source.system_id=f.system_id "
                        + "AND source.id=CAST(JSON_UNQUOTE(JSON_EXTRACT(f.property_json,'$.sourceFieldId')) AS UNSIGNED) "
                        + "AND source.deleted_at IS NULL "
                        + "LEFT JOIN un_module_field target ON target.system_id=f.system_id "
                        + "AND target.module_id=source.target_module_id "
                        + "AND target.id=CAST(JSON_UNQUOTE(JSON_EXTRACT(f.property_json,'$.targetFieldId')) AS UNSIGNED) "
                        + "AND target.deleted_at IS NULL "
                        + "WHERE f.system_id=? AND f.deleted_at IS NULL AND f.desired_status='ENABLED' "
                        + "AND f.field_type='REFERENCE' AND (source.id IS NULL OR source.field_type<>'RELATION' "
                        + "OR source.desired_status<>'ENABLED' OR JSON_EXTRACT(source.property_json,'$.multiple')=TRUE "
                        + "OR target.id IS NULL OR target.desired_status<>'ENABLED' "
                        + "OR target.field_type NOT IN ('TEXT','NUMBER','RATING','DATE','DATETIME','SWITCH'))",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> issues.add(Issue.blocker(
                        "REFERENCE_DEPENDENCY_INVALID", "FIELD", row.getLong("id"), "properties.sourceFieldId",
                        "REFERENCE requires one enabled single RELATION and one supported target scalar",
                        "Repair the source relation and target field")), systemId);

        jdbc.query("SELECT DISTINCT parent.id FROM un_module_field parent "
                        + "JOIN un_module_config_reference ref ON ref.system_id=parent.system_id "
                        + "AND ref.source_type='FIELD' AND ref.source_id=parent.id "
                        + "AND ref.relation_type='DISPLAYS_COLUMN' "
                        + "LEFT JOIN un_module_field col ON col.system_id=ref.system_id AND col.id=ref.target_id "
                        + "AND col.deleted_at IS NULL "
                        + "WHERE parent.system_id=? AND parent.deleted_at IS NULL "
                        + "AND parent.desired_status='ENABLED' AND parent.field_type='SUBTABLE' "
                        + "AND (col.id IS NULL OR col.desired_status<>'ENABLED' OR col.is_readonly=TRUE "
                        + "OR col.field_type IN ('RELATION','REFERENCE','SUBTABLE','FORMULA','SUMMARY','CALCULATED',"
                        + "'LOOKUP','AGGREGATE','AUTO_NUMBER','CREATED_BY','CREATED_AT','UPDATED_BY','UPDATED_AT',"
                        + "'ATTACHMENT','IMAGE','FILE_GROUP','SIGNATURE','AI_FILL','TENANT'))",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> issues.add(Issue.blocker(
                        "SUBTABLE_COLUMN_INVALID", "FIELD", row.getLong("id"), "properties.columnFieldIds",
                        "SUBTABLE columns must remain enabled writable P4 field types",
                        "Remove unavailable columns or restore their supported configuration")), systemId);
    }

    private void inspectStatusFields(long systemId, List<Issue> issues) {
        jdbc.query("SELECT f.id,f.dictionary_id,f.property_json,d.dictionary_type "
                        + "FROM un_module_field f LEFT JOIN un_module_dictionary d "
                        + "ON d.system_id=f.system_id AND d.id=f.dictionary_id AND d.deleted_at IS NULL "
                        + "WHERE f.system_id=? AND f.deleted_at IS NULL AND f.desired_status='ENABLED' "
                        + "AND f.field_type='STATUS'",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> inspectStatusField(
                        systemId, row.getLong("id"), row.getLong("dictionary_id"),
                        row.getString("dictionary_type"), row.getString("property_json"), issues),
                systemId);
    }

    private void inspectStatusField(
            long systemId,
            long fieldId,
            long dictionaryId,
            String dictionaryType,
            String propertyJson,
            List<Issue> issues
    ) {
        if (!"STATUS".equals(dictionaryType)) {
            issues.add(Issue.blocker("STATUS_DICTIONARY_INVALID", "FIELD", fieldId,
                    "dictionaryId", "STATUS fields require an enabled STATUS dictionary",
                    "Select a STATUS dictionary"));
            return;
        }
        var enabled = new HashSet<String>(jdbc.query(
                "SELECT id FROM un_module_dictionary_item WHERE system_id=? AND dictionary_id=? "
                        + "AND deleted_at IS NULL AND desired_status='ENABLED'",
                (item, number) -> Long.toString(item.getLong("id")), systemId, dictionaryId));
        try {
            var properties = objectMapper.readTree(propertyJson);
            var initial = new HashSet<String>();
            properties.path("initialStateIds").forEach(value -> initial.add(value.asText()));
            var adjacency = new java.util.LinkedHashMap<String, Set<String>>();
            properties.path("transitions").forEach(edge -> adjacency
                    .computeIfAbsent(edge.path("from").asText(), ignored -> new HashSet<>())
                    .add(edge.path("to").asText()));
            var referenced = new HashSet<>(initial);
            adjacency.forEach((from, targets) -> {
                referenced.add(from);
                referenced.addAll(targets);
            });
            if (!enabled.containsAll(referenced)) {
                issues.add(Issue.blocker("STATUS_TRANSITION_TARGET_INVALID", "FIELD", fieldId,
                        "properties.transitions", "STATUS references disabled or unknown items",
                        "Use enabled items from the selected STATUS dictionary"));
                return;
            }
            var reached = new HashSet<>(initial);
            var queue = new ArrayDeque<>(initial);
            while (!queue.isEmpty()) {
                for (var target : adjacency.getOrDefault(queue.removeFirst(), Set.of())) {
                    if (reached.add(target)) {
                        queue.addLast(target);
                    }
                }
            }
            if (!reached.containsAll(enabled)) {
                issues.add(Issue.blocker("STATUS_STATE_UNREACHABLE", "FIELD", fieldId,
                        "properties.transitions", "One or more enabled STATUS states are unreachable",
                        "Add a transition path from an initial state or disable the unreachable item"));
            }
        } catch (JsonProcessingException exception) {
            issues.add(Issue.blocker("STATUS_PROPERTIES_INVALID", "FIELD", fieldId,
                    "properties", "STATUS properties are not valid JSON",
                    "Repair the STATUS field properties"));
        }
    }

    private long count(String table, String predicate, long systemId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE system_id=? AND deleted_at IS NULL AND "
                + predicate, Long.class, systemId);
    }

    private void ensureRoot(ConfigSession session) {
        var now = LocalDateTime.now();
        jdbc.update("INSERT INTO un_module_config_root (id,system_id,status,draft_revision,created_at,created_by,updated_at,updated_by,version) VALUES (?,?,'CLEAN',0,?,?,?,?,0) ON DUPLICATE KEY UPDATE system_id=VALUES(system_id)",
                ids.nextId(), session.systemId(), now, session.accountId(), now, session.accountId());
    }

    private ConfigViews.CheckReport view(long id, String status, int blockers, int warnings, long revision,
                                         ConfigSnapshotService.Snapshot snapshot, LocalDateTime expires,
                                         List<Issue> issues) {
        return new ConfigViews.CheckReport(Long.toString(id), status, blockers, warnings, Long.toString(revision),
                snapshot.checksum(), snapshot.sizeBytes(), expires.toString(),
                issues.stream().map(Issue::view).toList(), "0");
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize configuration check report", exception);
        }
    }

    private static String nullableId(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    record FieldPermissionStageFinding(long fieldId, String direction, String code) { }

    private record GenericFieldPermission(long fieldId, String direction, String code) { }

    private static final class Issue {
        private long id;
        private final String severity;
        private final String code;
        private final String resourceType;
        private final Long resourceId;
        private final String propertyPath;
        private final String message;
        private final String suggestedAction;

        private Issue(String severity, String code, String resourceType, Long resourceId, String propertyPath,
                      String message, String suggestedAction) {
            this.severity = severity;
            this.code = code;
            this.resourceType = resourceType;
            this.resourceId = resourceId;
            this.propertyPath = propertyPath;
            this.message = message;
            this.suggestedAction = suggestedAction;
        }

        static Issue blocker(String code, String type, Long id, String path, String message, String action) {
            return new Issue("BLOCKER", code, type, id, path, message, action);
        }

        static Issue warning(String code, String type, Long id, String path, String message, String action) {
            return new Issue("WARNING", code, type, id, path, message, action);
        }

        ConfigViews.CheckIssue view() {
            return new ConfigViews.CheckIssue(Long.toString(id), severity, code, resourceType,
                    resourceId == null ? null : Long.toString(resourceId), propertyPath, message, suggestedAction);
        }

        String severity() { return severity; }
        String code() { return code; }
        String resourceType() { return resourceType; }
        Long resourceId() { return resourceId; }
        String propertyPath() { return propertyPath; }
        String message() { return message; }
        String suggestedAction() { return suggestedAction; }
    }
}
