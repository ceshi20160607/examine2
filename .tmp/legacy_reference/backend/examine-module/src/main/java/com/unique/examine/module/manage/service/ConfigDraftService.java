package com.unique.examine.module.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.manage.api.ConfigRequests;
import com.unique.examine.module.manage.api.ConfigTypes;
import com.unique.examine.module.manage.api.ConfigViews;
import com.unique.examine.module.manage.api.FieldPermissionCodes;
import com.unique.examine.module.manage.security.ConfigSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static com.unique.examine.module.manage.api.ConfigTypes.*;

@Service
public class ConfigDraftService {
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");
    private static final Pattern ITEM_CODE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$");
    private static final Set<FieldType> P4_C1_SCALAR_FIELDS = Set.of(
            FieldType.PERCENT, FieldType.MONEY, FieldType.TIME, FieldType.SWITCH,
            FieldType.RATING, FieldType.PROGRESS);
    private static final Set<FieldType> P4_C1_COLLECTION_FIELDS = Set.of(
            FieldType.DATE_RANGE, FieldType.TIME_RANGE, FieldType.MULTI_SELECT,
            FieldType.CASCADE, FieldType.TAG);
    private static final Set<FieldType> P4_C2_FIELDS = Set.of(
            FieldType.PHONE, FieldType.EMAIL, FieldType.URL, FieldType.IDENTITY, FieldType.ADDRESS,
            FieldType.GEO, FieldType.BARCODE, FieldType.RICH_TEXT, FieldType.JSON, FieldType.SECRET,
            FieldType.STATUS);
    private static final Set<FieldType> P4_C2_UNIQUE_FIELDS = Set.of(
            FieldType.PHONE, FieldType.EMAIL, FieldType.URL, FieldType.IDENTITY,
            FieldType.BARCODE, FieldType.SECRET);
    private static final Set<FieldType> P4_C2_SORTABLE_FIELDS = Set.of(FieldType.BARCODE, FieldType.STATUS);
    private static final Set<FieldType> P4_C4_DERIVED_FIELDS = Set.of(
            FieldType.FORMULA, FieldType.SUMMARY, FieldType.CALCULATED,
            FieldType.LOOKUP, FieldType.AGGREGATE, FieldType.AI_FILL
    );
    private static final Set<FieldType> SYSTEM_COMPUTED_FIELDS = Set.of(
            FieldType.TENANT, FieldType.AUTO_NUMBER, FieldType.CREATED_BY, FieldType.CREATED_AT,
            FieldType.UPDATED_BY, FieldType.UPDATED_AT
    );
    private static final String FIELD_SELECT = "SELECT f.*,pr.desired_status read_permission_status,"
            + "pw.desired_status write_permission_status FROM un_module_field f "
            + "JOIN un_module_definition m ON m.system_id=f.system_id AND m.id=f.module_id "
            + "LEFT JOIN un_module_permission pr ON pr.system_id=f.system_id AND pr.resource_type='FIELD' "
            + "AND pr.resource_id=f.id AND pr.permission_code=CONCAT('module.',m.module_code,'.field.',"
            + "f.field_code,'.read') AND pr.deleted_at IS NULL "
            + "LEFT JOIN un_module_permission pw ON pw.system_id=f.system_id AND pw.resource_type='FIELD' "
            + "AND pw.resource_id=f.id AND pw.permission_code=CONCAT('module.',m.module_code,'.field.',"
            + "f.field_code,'.write') AND pw.deleted_at IS NULL ";
    private final JdbcTemplate jdbc;
    private final IdService ids;
    private final DraftRevisionCoordinator revisions;
    private final ConfigMutationSupport mutations;
    private final StructuredPropertyValidator validator;

    public ConfigDraftService(JdbcTemplate jdbc, IdService ids, DraftRevisionCoordinator revisions,
                              ConfigMutationSupport mutations, StructuredPropertyValidator validator) {
        this.jdbc = jdbc;
        this.ids = ids;
        this.revisions = revisions;
        this.mutations = mutations;
        this.validator = validator;
    }

    public ConfigViews.RootSummary root(long systemId) {
        return revisions.summary(systemId);
    }

    public List<ConfigViews.Group> groups(long systemId) {
        return jdbc.query("SELECT * FROM un_module_group WHERE system_id=? AND deleted_at IS NULL "
                + "ORDER BY sort_order,id", ConfigDraftService::group, systemId);
    }

    @Transactional
    public ConfigViews.Group createGroup(ConfigSession s, ConfigRequests.CreateGroup r, String key, RequestContext c) {
        return mutations.idempotent(s.systemId() + ":group:create", key, r, ConfigViews.Group.class,
                () -> createGroupNow(s, r, key, c));
    }

    private ConfigViews.Group createGroupNow(ConfigSession s, ConfigRequests.CreateGroup r, String key, RequestContext c) {
        code(r.code());
        var rev = revisions.begin(s, r.draftRevision());
        var id = ids.nextId();
        var now = LocalDateTime.now();
        insert(() -> jdbc.update("INSERT INTO un_module_group (id,system_id,group_code,group_name,description,"
                        + "icon_key,sort_order,desired_status,created_revision,updated_revision,created_at,created_by,"
                        + "updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                id, s.systemId(), normCode(r.code()), r.name().trim(), blank(r.description()), blank(r.iconKey()),
                r.sortOrder(), r.status().name(), rev.next(), rev.next(), now, s.accountId(), now, s.accountId()));
        revisions.finish(s, rev);
        var after = requireGroup(s.systemId(), id);
        changed(s, "GROUP", id, "MODULE_GROUP_CREATE", null, after, rev.next(), key, c);
        return after;
    }

    @Transactional
    public ConfigViews.Group updateGroup(ConfigSession s, long id, ConfigRequests.UpdateGroup r, RequestContext c) {
        code(r.code());
        var before = requireGroup(s.systemId(), id);
        requirePublishedCodeUnchanged("un_module_group", "group_code", s.systemId(), id, normCode(r.code()));
        var rev = revisions.begin(s, r.draftRevision());
        updated(jdbc.update("UPDATE un_module_group SET group_code=?,group_name=?,description=?,icon_key=?,sort_order=?,"
                        + "desired_status=?,updated_revision=?,updated_at=?,updated_by=?,version=version+1 "
                        + "WHERE id=? AND system_id=? AND deleted_at IS NULL AND version=?",
                normCode(r.code()), r.name().trim(), blank(r.description()), blank(r.iconKey()), r.sortOrder(),
                r.status().name(), rev.next(), LocalDateTime.now(), s.accountId(), id, s.systemId(), version(r.version())));
        revisions.finish(s, rev);
        var after = requireGroup(s.systemId(), id);
        changed(s, "GROUP", id, "MODULE_GROUP_UPDATE", before, after, rev.next(), c.requestId(), c);
        return after;
    }

    @Transactional
    public ConfigViews.RevisionResult deleteGroup(ConfigSession s, long id, ConfigRequests.DeleteResource r, RequestContext c) {
        var before = requireGroup(s.systemId(), id);
        if (count("un_module_definition", "group_id", s.systemId(), id) > 0) {
            throw ConfigErrors.invalidReference("模块组仍包含模块，不能删除");
        }
        var rev = revisions.begin(s, r.draftRevision());
        updated(softDelete("un_module_group", s, id, version(r.version()), rev.next()));
        revisions.finish(s, rev);
        changed(s, "GROUP", id, "MODULE_GROUP_DELETE", before, null, rev.next(), c.requestId(), c);
        return new ConfigViews.RevisionResult(Long.toString(rev.next()));
    }

    public List<ConfigViews.Module> modules(long systemId) {
        return jdbc.query("SELECT * FROM un_module_definition WHERE system_id=? AND deleted_at IS NULL "
                + "ORDER BY sort_order,id", ConfigDraftService::module, systemId);
    }

    @Transactional
    public ConfigViews.Module createModule(ConfigSession s, ConfigRequests.CreateModule r, String key, RequestContext c) {
        return mutations.idempotent(s.systemId() + ":module:create", key, r, ConfigViews.Module.class,
                () -> createModuleNow(s, r, key, c));
    }

    private ConfigViews.Module createModuleNow(ConfigSession s, ConfigRequests.CreateModule r, String key, RequestContext c) {
        code(r.code());
        var groupId = ConfigErrors.id(r.groupId(), "groupId");
        requireGroup(s.systemId(), groupId);
        var rev = revisions.begin(s, r.draftRevision());
        var id = ids.nextId();
        var now = LocalDateTime.now();
        insert(() -> jdbc.update("INSERT INTO un_module_definition (id,system_id,group_id,module_code,module_name,"
                        + "description,icon_key,sort_order,desired_status,allow_comments,allow_team,created_revision,"
                        + "updated_revision,created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)", id, s.systemId(), groupId, normCode(r.code()),
                r.name().trim(), blank(r.description()), blank(r.iconKey()), r.sortOrder(), r.status().name(),
                r.allowComments(), r.allowTeam(), rev.next(), rev.next(), now, s.accountId(), now, s.accountId()));
        for (var type : PageType.values()) {
            insertDefaultPage(s, id, type, rev.next(), now);
        }
        insertModulePermissions(s, id, normCode(r.code()), r.name().trim(), rev.next(), now);
        revisions.finish(s, rev);
        var after = requireModule(s.systemId(), id);
        changed(s, "MODULE", id, "MODULE_CREATE", null, after, rev.next(), key, c);
        return after;
    }

    @Transactional
    public ConfigViews.Module updateModule(ConfigSession s, long id, ConfigRequests.UpdateModule r, RequestContext c) {
        code(r.code());
        var before = requireModule(s.systemId(), id);
        var groupId = ConfigErrors.id(r.groupId(), "groupId");
        requireGroup(s.systemId(), groupId);
        if (!before.code().equals(normCode(r.code()))) {
            throw ConfigErrors.invalid("模块编码创建后不可修改");
        }
        var rev = revisions.begin(s, r.draftRevision());
        updated(jdbc.update("UPDATE un_module_definition SET group_id=?,module_name=?,description=?,icon_key=?,"
                        + "sort_order=?,desired_status=?,allow_comments=?,allow_team=?,updated_revision=?,updated_at=?,"
                        + "updated_by=?,version=version+1 WHERE id=? AND system_id=? AND deleted_at IS NULL AND version=?",
                groupId, r.name().trim(), blank(r.description()), blank(r.iconKey()), r.sortOrder(), r.status().name(),
                r.allowComments(), r.allowTeam(), rev.next(), LocalDateTime.now(), s.accountId(), id, s.systemId(),
                version(r.version())));
        revisions.finish(s, rev);
        var after = requireModule(s.systemId(), id);
        changed(s, "MODULE", id, "MODULE_UPDATE", before, after, rev.next(), c.requestId(), c);
        return after;
    }

    @Transactional
    public ConfigViews.RevisionResult deleteModule(ConfigSession s, long id, ConfigRequests.DeleteResource r, RequestContext c) {
        var before = requireModule(s.systemId(), id);
        var rev = revisions.begin(s, r.draftRevision());
        var now = LocalDateTime.now();
        deleteReferences(s.systemId(), "MODULE", List.of(id));
        deleteReferences(s.systemId(), "FIELD", idsForModule("un_module_field", s.systemId(), id));
        deleteReferences(s.systemId(), "PAGE", idsForModule("un_module_page", s.systemId(), id));
        deleteReferences(s.systemId(), "COMPONENT", jdbc.queryForList(
                "SELECT c.id FROM un_module_page_component c JOIN un_module_page p "
                        + "ON p.system_id=c.system_id AND p.id=c.page_id "
                        + "WHERE c.system_id=? AND p.module_id=?", Long.class, s.systemId(), id));
        deleteReferences(s.systemId(), "ACTION", idsForModule("un_module_action", s.systemId(), id));
        deleteReferences(s.systemId(), "RULE", idsForModule("un_module_rule", s.systemId(), id));
        jdbc.update("UPDATE un_module_page_component c JOIN un_module_page p "
                        + "ON p.system_id=c.system_id AND p.id=c.page_id "
                        + "SET c.deleted_at=?,c.deleted_by=?,c.updated_revision=?,c.updated_at=?,c.updated_by=?,"
                        + "c.version=c.version+1 WHERE c.system_id=? AND p.module_id=? AND c.deleted_at IS NULL",
                now, s.accountId(), rev.next(), now, s.accountId(), s.systemId(), id);
        for (var table : List.of("un_module_field", "un_module_page", "un_module_action", "un_module_rule", "un_module_permission")) {
            jdbc.update("UPDATE " + table + " SET deleted_at=?,deleted_by=?,updated_revision=?,updated_at=?,updated_by=?,"
                    + "version=version+1 WHERE system_id=? AND module_id=? AND deleted_at IS NULL",
                    now, s.accountId(), rev.next(), now, s.accountId(), s.systemId(), id);
        }
        updated(softDelete("un_module_definition", s, id, version(r.version()), rev.next()));
        revisions.finish(s, rev);
        changed(s, "MODULE", id, "MODULE_DELETE", before, null, rev.next(), c.requestId(), c);
        return new ConfigViews.RevisionResult(Long.toString(rev.next()));
    }

    private List<Long> idsForModule(String table, long systemId, long moduleId) {
        return jdbc.queryForList("SELECT id FROM " + table + " WHERE system_id=? AND module_id=?", Long.class,
                systemId, moduleId);
    }

    private void deleteReferences(long systemId, String resourceType, List<Long> resourceIds) {
        for (var resourceId : resourceIds) {
            jdbc.update("DELETE FROM un_module_config_reference WHERE system_id=? AND "
                            + "((source_type=? AND source_id=?) OR (target_type=? AND target_id=?))",
                    systemId, resourceType, resourceId, resourceType, resourceId);
        }
    }

    public List<ConfigViews.Dictionary> dictionaries(long systemId) {
        return jdbc.query("SELECT * FROM un_module_dictionary WHERE system_id=? AND deleted_at IS NULL "
                + "ORDER BY dictionary_name,id", ConfigDraftService::dictionary, systemId);
    }

    @Transactional
    public ConfigViews.Dictionary createDictionary(ConfigSession s, ConfigRequests.CreateDictionary r, String key,
                                                   RequestContext c) {
        return mutations.idempotent(s.systemId() + ":dictionary:create", key, r, ConfigViews.Dictionary.class,
                () -> createDictionaryNow(s, r, key, c));
    }

    private ConfigViews.Dictionary createDictionaryNow(ConfigSession s, ConfigRequests.CreateDictionary r, String key,
                                                        RequestContext c) {
        code(r.code());
        var rev = revisions.begin(s, r.draftRevision());
        var id = ids.nextId();
        var now = LocalDateTime.now();
        insert(() -> jdbc.update("INSERT INTO un_module_dictionary (id,system_id,dictionary_code,dictionary_name,"
                        + "dictionary_type,category,description,desired_status,created_revision,updated_revision,created_at,"
                        + "created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                id, s.systemId(), normCode(r.code()), r.name().trim(), r.type().name(), blank(r.category()),
                blank(r.description()), r.status().name(), rev.next(), rev.next(), now, s.accountId(), now, s.accountId()));
        revisions.finish(s, rev);
        var after = requireDictionary(s.systemId(), id);
        changed(s, "DICTIONARY", id, "DICTIONARY_CREATE", null, after, rev.next(), key, c);
        return after;
    }

    @Transactional
    public ConfigViews.Dictionary updateDictionary(ConfigSession s, long id, ConfigRequests.UpdateDictionary r,
                                                   RequestContext c) {
        code(r.code());
        var before = requireDictionary(s.systemId(), id);
        if (!before.code().equals(normCode(r.code()))) throw ConfigErrors.invalid("字典编码创建后不可修改");
        if (before.type() != r.type() && count("un_module_dictionary_item", "dictionary_id", s.systemId(), id) > 0) {
            throw ConfigErrors.invalidReference("已有字典项时不能修改字典类型");
        }
        var rev = revisions.begin(s, r.draftRevision());
        updated(jdbc.update("UPDATE un_module_dictionary SET dictionary_name=?,dictionary_type=?,category=?,description=?,"
                        + "desired_status=?,updated_revision=?,updated_at=?,updated_by=?,version=version+1 "
                        + "WHERE id=? AND system_id=? AND deleted_at IS NULL AND version=?", r.name().trim(), r.type().name(),
                blank(r.category()), blank(r.description()), r.status().name(), rev.next(), LocalDateTime.now(),
                s.accountId(), id, s.systemId(), version(r.version())));
        revisions.finish(s, rev);
        var after = requireDictionary(s.systemId(), id);
        changed(s, "DICTIONARY", id, "DICTIONARY_UPDATE", before, after, rev.next(), c.requestId(), c);
        return after;
    }

    @Transactional
    public ConfigViews.RevisionResult deleteDictionary(ConfigSession s, long id, ConfigRequests.DeleteResource r,
                                                       RequestContext c) {
        var before = requireDictionary(s.systemId(), id);
        if (count("un_module_dictionary_item", "dictionary_id", s.systemId(), id) > 0
                || jdbc.queryForObject("SELECT COUNT(*) FROM un_module_field WHERE system_id=? AND dictionary_id=? "
                + "AND deleted_at IS NULL", Long.class, s.systemId(), id) > 0) {
            throw ConfigErrors.invalidReference("字典仍有字典项或字段引用，不能删除");
        }
        var rev = revisions.begin(s, r.draftRevision());
        updated(softDelete("un_module_dictionary", s, id, version(r.version()), rev.next()));
        revisions.finish(s, rev);
        changed(s, "DICTIONARY", id, "DICTIONARY_DELETE", before, null, rev.next(), c.requestId(), c);
        return new ConfigViews.RevisionResult(Long.toString(rev.next()));
    }

    public List<ConfigViews.DictionaryItem> dictionaryItems(long systemId, long dictionaryId) {
        requireDictionary(systemId, dictionaryId);
        return jdbc.query("SELECT * FROM un_module_dictionary_item WHERE system_id=? AND dictionary_id=? "
                + "AND deleted_at IS NULL ORDER BY depth_level,sort_order,id", ConfigDraftService::item,
                systemId, dictionaryId);
    }

    @Transactional
    public ConfigViews.DictionaryItem createItem(ConfigSession s, long dictionaryId,
                                                 ConfigRequests.CreateDictionaryItem r, String key, RequestContext c) {
        return mutations.idempotent(s.systemId() + ":dictionary:" + dictionaryId + ":item:create", key, r,
                ConfigViews.DictionaryItem.class, () -> createItemNow(s, dictionaryId, r, key, c));
    }

    private ConfigViews.DictionaryItem createItemNow(ConfigSession s, long dictionaryId,
                                                      ConfigRequests.CreateDictionaryItem r, String key, RequestContext c) {
        itemCode(r.code());
        var dictionary = requireDictionary(s.systemId(), dictionaryId);
        var parentId = ConfigErrors.nullableId(r.parentId(), "parentId");
        ConfigViews.DictionaryItem parent = parentId == null ? null : requireItem(s.systemId(), dictionaryId, parentId);
        validateTreeParent(dictionary.type(), parent);
        validateDefault(r.isDefault(), r.status());
        var depth = parent == null ? 0 : parent.depth() + 1;
        if (depth > 16) throw ConfigErrors.invalidReference("字典树最大深度为 16");
        var rev = revisions.begin(s, r.draftRevision());
        var id = ids.nextId();
        var now = LocalDateTime.now();
        var path = parent == null ? "/" + id : itemPath(s.systemId(), dictionaryId, parent.id()) + "/" + id;
        insert(() -> jdbc.update("INSERT INTO un_module_dictionary_item (id,system_id,dictionary_id,parent_id,item_code,"
                        + "item_label,semantic_key,color_value,icon_key,sort_order,depth_level,depth_path,is_default,"
                        + "desired_status,created_revision,updated_revision,created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)", id, s.systemId(), dictionaryId, parentId,
                r.code().trim(), r.label().trim(), blank(r.semanticKey()), blank(r.color()), blank(r.iconKey()),
                r.sortOrder(), depth, path, r.isDefault(), r.status().name(), rev.next(), rev.next(), now,
                s.accountId(), now, s.accountId()));
        jdbc.update("INSERT INTO un_module_dictionary_item_closure "
                        + "(id,system_id,dictionary_id,ancestor_id,descendant_id,depth,created_at,created_by) "
                        + "VALUES (?,?,?,?,?,0,?,?)", ids.nextId(), s.systemId(), dictionaryId, id, id, now, s.accountId());
        if (parentId != null) {
            var ancestors = jdbc.query("SELECT ancestor_id,depth FROM un_module_dictionary_item_closure "
                    + "WHERE system_id=? AND dictionary_id=? AND descendant_id=?", (rs, n) ->
                    new long[]{rs.getLong(1), rs.getLong(2)}, s.systemId(), dictionaryId, parentId);
            for (var ancestor : ancestors) {
                jdbc.update("INSERT INTO un_module_dictionary_item_closure "
                                + "(id,system_id,dictionary_id,ancestor_id,descendant_id,depth,created_at,created_by) "
                                + "VALUES (?,?,?,?,?,?,?,?)", ids.nextId(), s.systemId(), dictionaryId, ancestor[0], id,
                        ancestor[1] + 1, now, s.accountId());
            }
        }
        revisions.finish(s, rev);
        var after = requireItem(s.systemId(), dictionaryId, id);
        changed(s, "DICTIONARY_ITEM", id, "DICTIONARY_ITEM_CREATE", null, after, rev.next(), key, c);
        return after;
    }

    @Transactional
    public ConfigViews.DictionaryItem updateItem(ConfigSession s, long dictionaryId, long id,
                                                 ConfigRequests.UpdateDictionaryItem r, RequestContext c) {
        itemCode(r.code());
        var before = requireItem(s.systemId(), dictionaryId, id);
        requirePublishedCodeUnchanged("un_module_dictionary_item", "item_code", s.systemId(), id, r.code().trim());
        var requestedParent = ConfigErrors.nullableId(r.parentId(), "parentId");
        var currentParent = before.parentId() == null ? null : Long.parseLong(before.parentId());
        if (!Objects.equals(requestedParent, currentParent)) {
            throw ConfigErrors.invalidReference("最小纵切暂不支持字典项换父级；请新建目标项后迁移引用");
        }
        validateDefault(r.isDefault(), r.status());
        var rev = revisions.begin(s, r.draftRevision());
        updated(jdbc.update("UPDATE un_module_dictionary_item SET item_code=?,item_label=?,semantic_key=?,color_value=?,"
                        + "icon_key=?,sort_order=?,is_default=?,desired_status=?,updated_revision=?,updated_at=?,updated_by=?,"
                        + "version=version+1 WHERE id=? AND system_id=? AND dictionary_id=? AND deleted_at IS NULL AND version=?",
                r.code().trim(), r.label().trim(), blank(r.semanticKey()), blank(r.color()), blank(r.iconKey()), r.sortOrder(),
                r.isDefault(), r.status().name(), rev.next(), LocalDateTime.now(), s.accountId(), id, s.systemId(),
                dictionaryId, version(r.version())));
        revisions.finish(s, rev);
        var after = requireItem(s.systemId(), dictionaryId, id);
        changed(s, "DICTIONARY_ITEM", id, "DICTIONARY_ITEM_UPDATE", before, after, rev.next(), c.requestId(), c);
        return after;
    }

    @Transactional
    public ConfigViews.RevisionResult deleteItem(ConfigSession s, long dictionaryId, long id,
                                                 ConfigRequests.DeleteResource r, RequestContext c) {
        var before = requireItem(s.systemId(), dictionaryId, id);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM un_module_dictionary_item WHERE system_id=? AND dictionary_id=? "
                + "AND parent_id=? AND deleted_at IS NULL", Long.class, s.systemId(), dictionaryId, id) > 0) {
            throw ConfigErrors.invalidReference("字典项仍有子项，不能删除");
        }
        var rev = revisions.begin(s, r.draftRevision());
        jdbc.update("DELETE FROM un_module_dictionary_item_closure WHERE system_id=? AND dictionary_id=? "
                + "AND descendant_id=?", s.systemId(), dictionaryId, id);
        updated(softDelete("un_module_dictionary_item", s, id, version(r.version()), rev.next()));
        revisions.finish(s, rev);
        changed(s, "DICTIONARY_ITEM", id, "DICTIONARY_ITEM_DELETE", before, null, rev.next(), c.requestId(), c);
        return new ConfigViews.RevisionResult(Long.toString(rev.next()));
    }

    public List<ConfigViews.Field> fields(long systemId, long moduleId) {
        requireModule(systemId, moduleId);
        return jdbc.query(FIELD_SELECT + "WHERE f.system_id=? AND f.module_id=? AND f.deleted_at IS NULL "
                + "ORDER BY f.sort_order,f.id", (rs,n)->fieldRow(rs), systemId, moduleId);
    }

    @Transactional
    public ConfigViews.Field createField(ConfigSession s, long moduleId, ConfigRequests.CreateField r, String key,
                                         RequestContext c) {
        return mutations.idempotent(s.systemId() + ":module:" + moduleId + ":field:create", key, r,
                ConfigViews.Field.class, () -> createFieldNow(s, moduleId, r, key, c));
    }

    private ConfigViews.Field createFieldNow(ConfigSession s, long moduleId, ConfigRequests.CreateField r, String key,
                                             RequestContext c) {
        code(r.code()); var module = requireModule(s.systemId(), moduleId);
        var readPermissionMode = createPermissionMode(r.readPermissionMode());
        var writePermissionMode = createPermissionMode(r.writePermissionMode());
        validateFieldPermissionModes(r.type(), r.readonly(), writePermissionMode);
        validateFieldCapabilities(r.type(), r.searchable(), r.filterable(), r.indexMode(), r.properties());
        validateDerivedEnvelope(r.type(), r.required(), r.readonly());
        var json = validator.field(r.type(), r.properties());
        validateFieldRefs(s.systemId(), moduleId, r.type(), r.dictionaryId(), r.targetModuleId(), r.properties());
        var rev = revisions.begin(s, r.draftRevision()); var id = ids.nextId(); var now = LocalDateTime.now();
        insert(() -> jdbc.update("INSERT INTO un_module_field (id,system_id,module_id,dictionary_id,target_module_id,"
                        + "field_code,field_name,field_type,sort_order,is_required,is_hidden,is_readonly,is_searchable,"
                        + "is_filterable,show_in_list,show_in_detail,index_mode,desired_status,property_json,created_revision,"
                        + "updated_revision,created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)", id, s.systemId(), moduleId,
                nid(r.dictionaryId(), "dictionaryId"), nid(r.targetModuleId(), "targetModuleId"), normCode(r.code()),
                r.name().trim(), r.type().name(), r.sortOrder(), r.required(), r.hidden(), r.readonly(), r.searchable(),
                r.filterable(), r.showInList(), r.showInDetail(), r.indexMode().name(), r.status().name(), json,
                rev.next(), rev.next(), now, s.accountId(), now, s.accountId()));
        syncFieldPermissions(s, module, id, r.name().trim(), r.type(), normCode(r.code()),
                readPermissionMode, writePermissionMode, rev.next(), now);
        replaceFieldRefs(s, moduleId, id, r.dictionaryId(), r.targetModuleId(), r.properties(), rev.next(), now);
        revisions.finish(s, rev); var after = requireField(s.systemId(), moduleId, id);
        changed(s, "FIELD", id, "MODULE_FIELD_CREATE", null, after, rev.next(), key, c); return after;
    }

    @Transactional
    public ConfigViews.Field updateField(ConfigSession s, long moduleId, long id, ConfigRequests.UpdateField r,
                                         RequestContext c) {
        code(r.code()); var before = requireField(s.systemId(), moduleId, id);
        var module = requireModule(s.systemId(), moduleId);
        requirePublishedCodeUnchanged("un_module_field", "field_code", s.systemId(), id, normCode(r.code()));
        if (!before.code().equals(normCode(r.code()))) {
            requireNoFilterScenarioReference(s.systemId(), moduleId, before.code());
        }
        var readPermissionMode = updatePermissionMode(r.readPermissionMode(), before.readPermissionMode());
        var writePermissionMode = updatePermissionMode(r.writePermissionMode(), before.writePermissionMode());
        validateFieldPermissionModes(r.type(), r.readonly(), writePermissionMode);
        validateFieldCapabilities(r.type(), r.searchable(), r.filterable(), r.indexMode(), r.properties());
        validateDerivedEnvelope(r.type(), r.required(), r.readonly());
        var json=validator.field(r.type(),r.properties()); validateFieldRefs(s.systemId(), moduleId, r.type(), r.dictionaryId(), r.targetModuleId(), r.properties());
        var rev=revisions.begin(s,r.draftRevision()); updated(jdbc.update("UPDATE un_module_field SET dictionary_id=?,"
                        + "target_module_id=?,field_code=?,field_name=?,field_type=?,sort_order=?,is_required=?,is_hidden=?,"
                        + "is_readonly=?,is_searchable=?,is_filterable=?,show_in_list=?,show_in_detail=?,index_mode=?,"
                        + "desired_status=?,property_json=?,updated_revision=?,updated_at=?,updated_by=?,version=version+1 "
                        + "WHERE id=? AND system_id=? AND module_id=? AND deleted_at IS NULL AND version=?",
                nid(r.dictionaryId(),"dictionaryId"),nid(r.targetModuleId(),"targetModuleId"),normCode(r.code()),r.name().trim(),
                r.type().name(),r.sortOrder(),r.required(),r.hidden(),r.readonly(),r.searchable(),r.filterable(),r.showInList(),
                r.showInDetail(),r.indexMode().name(),r.status().name(),json,rev.next(),LocalDateTime.now(),s.accountId(),id,
                s.systemId(),moduleId,version(r.version())));
        syncFieldPermissions(s, module, id, r.name().trim(), r.type(), normCode(r.code()),
                readPermissionMode, writePermissionMode, rev.next(), LocalDateTime.now());
        replaceFieldRefs(s,moduleId,id,r.dictionaryId(),r.targetModuleId(),r.properties(),rev.next(),LocalDateTime.now());
        revisions.finish(s,rev); var after=requireField(s.systemId(),moduleId,id); changed(s,"FIELD",id,"MODULE_FIELD_UPDATE",before,after,rev.next(),c.requestId(),c); return after;
    }

    @Transactional
    public ConfigViews.RevisionResult deleteField(ConfigSession s,long moduleId,long id,ConfigRequests.DeleteResource r,RequestContext c){
        var before=requireField(s.systemId(),moduleId,id); if(referenceCount(s.systemId(),"FIELD",id)>0) throw ConfigErrors.invalidReference("字段仍被页面或规则引用");
        requireNoFilterScenarioReference(s.systemId(), moduleId, before.code());
        var rev=revisions.begin(s,r.draftRevision()); jdbc.update("DELETE FROM un_module_config_reference WHERE system_id=? AND source_type='FIELD' AND source_id=?",s.systemId(),id);
        softDeleteFieldPermissions(s, id, rev.next(), LocalDateTime.now());
        updated(softDelete("un_module_field",s,id,version(r.version()),rev.next())); revisions.finish(s,rev); changed(s,"FIELD",id,"MODULE_FIELD_DELETE",before,null,rev.next(),c.requestId(),c); return new ConfigViews.RevisionResult(Long.toString(rev.next()));
    }

    public List<ConfigViews.Page> pages(long systemId,long moduleId){requireModule(systemId,moduleId);return jdbc.query("SELECT * FROM un_module_page WHERE system_id=? AND module_id=? AND deleted_at IS NULL ORDER BY page_type,id",(rs,n)->pageRow(rs),systemId,moduleId);}
    @Transactional public ConfigViews.Page createPage(ConfigSession s,long moduleId,ConfigRequests.CreatePage r,String key,RequestContext c){return mutations.idempotent(s.systemId()+":module:"+moduleId+":page:create",key,r,ConfigViews.Page.class,()->createPageNow(s,moduleId,r,key,c));}
    private ConfigViews.Page createPageNow(ConfigSession s,long moduleId,ConfigRequests.CreatePage r,String key,RequestContext c){code(r.code());requireModule(s.systemId(),moduleId);var json=validator.layout(r.type(),r.layout());var rev=revisions.begin(s,r.draftRevision());var id=ids.nextId();var now=LocalDateTime.now();insert(()->jdbc.update("INSERT INTO un_module_page (id,system_id,module_id,page_code,page_name,page_type,is_default,desired_status,layout_json,created_revision,updated_revision,created_at,created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",id,s.systemId(),moduleId,normCode(r.code()),r.name().trim(),r.type().name(),r.isDefault(),r.status().name(),json,rev.next(),rev.next(),now,s.accountId(),now,s.accountId()));revisions.finish(s,rev);var after=requirePage(s.systemId(),moduleId,id);changed(s,"PAGE",id,"MODULE_PAGE_CREATE",null,after,rev.next(),key,c);return after;}
    @Transactional public ConfigViews.Page updatePage(ConfigSession s,long moduleId,long id,ConfigRequests.UpdatePage r,RequestContext c){code(r.code());var before=requirePage(s.systemId(),moduleId,id);requirePublishedCodeUnchanged("un_module_page","page_code",s.systemId(),id,normCode(r.code()));var json=validator.layout(r.type(),r.layout());var rev=revisions.begin(s,r.draftRevision());updated(jdbc.update("UPDATE un_module_page SET page_code=?,page_name=?,page_type=?,is_default=?,desired_status=?,layout_json=?,updated_revision=?,updated_at=?,updated_by=?,version=version+1 WHERE id=? AND system_id=? AND module_id=? AND deleted_at IS NULL AND version=?",normCode(r.code()),r.name().trim(),r.type().name(),r.isDefault(),r.status().name(),json,rev.next(),LocalDateTime.now(),s.accountId(),id,s.systemId(),moduleId,version(r.version())));revisions.finish(s,rev);var after=requirePage(s.systemId(),moduleId,id);changed(s,"PAGE",id,"MODULE_PAGE_UPDATE",before,after,rev.next(),c.requestId(),c);return after;}
    @Transactional
    public ConfigViews.RevisionResult deletePage(ConfigSession s,long moduleId,long id,ConfigRequests.DeleteResource r,RequestContext c){
        var before=requirePage(s.systemId(),moduleId,id);
        if(referenceCount(s.systemId(),"PAGE",id)>0)throw ConfigErrors.invalidReference("页面仍被动作引用");
        var rev=revisions.begin(s,r.draftRevision());var now=LocalDateTime.now();
        jdbc.update("DELETE FROM un_module_config_reference WHERE system_id=? AND source_type='COMPONENT' AND source_id IN (SELECT id FROM un_module_page_component WHERE system_id=? AND page_id=?)",s.systemId(),s.systemId(),id);
        jdbc.update("UPDATE un_module_page_component SET deleted_at=?,deleted_by=?,updated_revision=?,updated_at=?,updated_by=?,version=version+1 WHERE system_id=? AND page_id=? AND deleted_at IS NULL",now,s.accountId(),rev.next(),now,s.accountId(),s.systemId(),id);
        updated(softDelete("un_module_page",s,id,version(r.version()),rev.next()));revisions.finish(s,rev);
        changed(s,"PAGE",id,"MODULE_PAGE_DELETE",before,null,rev.next(),c.requestId(),c);return new ConfigViews.RevisionResult(Long.toString(rev.next()));
    }

    public List<ConfigViews.Component> components(long systemId,long moduleId,long pageId){
        requirePage(systemId,moduleId,pageId);
        return jdbc.query("SELECT * FROM un_module_page_component WHERE system_id=? AND page_id=? AND deleted_at IS NULL ORDER BY parent_component_id,sort_order,id",(rs,n)->componentRow(rs),systemId,pageId);
    }

    @Transactional
    public ConfigViews.Component createComponent(ConfigSession s,long moduleId,long pageId,ConfigRequests.CreateComponent r,String key,RequestContext c){
        return mutations.idempotent(s.systemId()+":page:"+pageId+":component:create",key,r,ConfigViews.Component.class,
                ()->createComponentNow(s,moduleId,pageId,r,key,c));
    }

    private ConfigViews.Component createComponentNow(ConfigSession s,long moduleId,long pageId,ConfigRequests.CreateComponent r,String key,RequestContext c){
        code(r.key());requirePage(s.systemId(),moduleId,pageId);
        var parentId=nid(r.parentComponentId(),"parentComponentId");
        var fieldId=nid(r.fieldId(),"fieldId");
        validateComponentRefs(s.systemId(),moduleId,pageId,null,parentId,fieldId,r.type(),r.properties());
        var json=validator.component(r.type(),r.fieldId(),r.properties());
        var rev=revisions.begin(s,r.draftRevision());var id=ids.nextId();var now=LocalDateTime.now();
        insert(()->jdbc.update("INSERT INTO un_module_page_component (id,system_id,page_id,parent_component_id,field_id,component_key,component_type,sort_order,grid_row,grid_column,grid_span,property_json,created_revision,updated_revision,created_at,created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",id,s.systemId(),pageId,parentId,fieldId,normCode(r.key()),r.type().name(),r.sortOrder(),r.gridRow(),r.gridColumn(),r.gridSpan(),json,rev.next(),rev.next(),now,s.accountId(),now,s.accountId()));
        replaceComponentRefs(s,id,fieldId,r.type(),r.properties(),moduleId,rev.next(),now);
        revisions.finish(s,rev);var after=requireComponent(s.systemId(),pageId,id);
        changed(s,"COMPONENT",id,"MODULE_COMPONENT_CREATE",null,after,rev.next(),key,c);return after;
    }

    @Transactional
    public ConfigViews.Component updateComponent(ConfigSession s,long moduleId,long pageId,long id,ConfigRequests.UpdateComponent r,RequestContext c){
        code(r.key());requirePage(s.systemId(),moduleId,pageId);var before=requireComponent(s.systemId(),pageId,id);
        var parentId=nid(r.parentComponentId(),"parentComponentId");var fieldId=nid(r.fieldId(),"fieldId");
        validateComponentRefs(s.systemId(),moduleId,pageId,id,parentId,fieldId,r.type(),r.properties());
        var json=validator.component(r.type(),r.fieldId(),r.properties());var rev=revisions.begin(s,r.draftRevision());var now=LocalDateTime.now();
        updated(jdbc.update("UPDATE un_module_page_component SET parent_component_id=?,field_id=?,component_key=?,component_type=?,sort_order=?,grid_row=?,grid_column=?,grid_span=?,property_json=?,updated_revision=?,updated_at=?,updated_by=?,version=version+1 WHERE id=? AND system_id=? AND page_id=? AND deleted_at IS NULL AND version=?",parentId,fieldId,normCode(r.key()),r.type().name(),r.sortOrder(),r.gridRow(),r.gridColumn(),r.gridSpan(),json,rev.next(),now,s.accountId(),id,s.systemId(),pageId,version(r.version())));
        replaceComponentRefs(s,id,fieldId,r.type(),r.properties(),moduleId,rev.next(),now);
        revisions.finish(s,rev);var after=requireComponent(s.systemId(),pageId,id);
        changed(s,"COMPONENT",id,"MODULE_COMPONENT_UPDATE",before,after,rev.next(),c.requestId(),c);return after;
    }

    @Transactional
    public ConfigViews.RevisionResult deleteComponent(ConfigSession s,long moduleId,long pageId,long id,ConfigRequests.DeleteResource r,RequestContext c){
        requirePage(s.systemId(),moduleId,pageId);var before=requireComponent(s.systemId(),pageId,id);
        if(count("un_module_page_component","parent_component_id",s.systemId(),id)>0)throw ConfigErrors.invalidReference("组件仍包含子组件");
        var rev=revisions.begin(s,r.draftRevision());
        jdbc.update("DELETE FROM un_module_config_reference WHERE system_id=? AND source_type='COMPONENT' AND source_id=?",s.systemId(),id);
        updated(softDelete("un_module_page_component",s,id,version(r.version()),rev.next()));revisions.finish(s,rev);
        changed(s,"COMPONENT",id,"MODULE_COMPONENT_DELETE",before,null,rev.next(),c.requestId(),c);return new ConfigViews.RevisionResult(Long.toString(rev.next()));
    }

    public List<ConfigViews.Action> actions(long systemId,long moduleId){requireModule(systemId,moduleId);return jdbc.query("SELECT * FROM un_module_action WHERE system_id=? AND module_id=? AND deleted_at IS NULL ORDER BY sort_order,id",(rs,n)->actionRow(rs),systemId,moduleId);}
    @Transactional public ConfigViews.Action createAction(ConfigSession s,long moduleId,ConfigRequests.CreateAction r,String key,RequestContext c){return mutations.idempotent(s.systemId()+":module:"+moduleId+":action:create",key,r,ConfigViews.Action.class,()->createActionNow(s,moduleId,r,key,c));}
    private ConfigViews.Action createActionNow(ConfigSession s,long moduleId,ConfigRequests.CreateAction r,String key,RequestContext c){code(r.code());var module=requireModule(s.systemId(),moduleId);validateActionRefs(s.systemId(),moduleId,r.properties());var json=validator.action(r.properties());var rev=revisions.begin(s,r.draftRevision());var id=ids.nextId();var now=LocalDateTime.now();var permission="module."+module.code()+".action."+normCode(r.code());insert(()->jdbc.update("INSERT INTO un_module_action (id,system_id,module_id,action_code,action_name,action_type,placement,permission_code,confirm_message,sort_order,desired_status,property_json,created_revision,updated_revision,created_at,created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",id,s.systemId(),moduleId,normCode(r.code()),r.name().trim(),r.type().name(),r.placement().name(),permission,blank(r.confirmMessage()),r.sortOrder(),r.status().name(),json,rev.next(),rev.next(),now,s.accountId(),now,s.accountId()));insertPermission(s,moduleId,id,"ACTION",permission,r.name(),"ACTION",rev.next(),now);replaceActionRefs(s,id,r.properties(),rev.next(),now);revisions.finish(s,rev);var after=requireAction(s.systemId(),moduleId,id);changed(s,"ACTION",id,"MODULE_ACTION_CREATE",null,after,rev.next(),key,c);return after;}
    @Transactional public ConfigViews.Action updateAction(ConfigSession s,long moduleId,long id,ConfigRequests.UpdateAction r,RequestContext c){code(r.code());var before=requireAction(s.systemId(),moduleId,id);if(!before.code().equals(normCode(r.code())))throw ConfigErrors.invalid("动作编码创建后不可修改");validateActionRefs(s.systemId(),moduleId,r.properties());var json=validator.action(r.properties());var rev=revisions.begin(s,r.draftRevision());var now=LocalDateTime.now();updated(jdbc.update("UPDATE un_module_action SET action_name=?,action_type=?,placement=?,confirm_message=?,sort_order=?,desired_status=?,property_json=?,updated_revision=?,updated_at=?,updated_by=?,version=version+1 WHERE id=? AND system_id=? AND module_id=? AND deleted_at IS NULL AND version=?",r.name().trim(),r.type().name(),r.placement().name(),blank(r.confirmMessage()),r.sortOrder(),r.status().name(),json,rev.next(),now,s.accountId(),id,s.systemId(),moduleId,version(r.version())));replaceActionRefs(s,id,r.properties(),rev.next(),now);revisions.finish(s,rev);var after=requireAction(s.systemId(),moduleId,id);changed(s,"ACTION",id,"MODULE_ACTION_UPDATE",before,after,rev.next(),c.requestId(),c);return after;}
    @Transactional public ConfigViews.RevisionResult deleteAction(ConfigSession s,long moduleId,long id,ConfigRequests.DeleteResource r,RequestContext c){var before=requireAction(s.systemId(),moduleId,id);if(referenceCount(s.systemId(),"ACTION",id)>0)throw ConfigErrors.invalidReference("动作仍被规则引用");var rev=revisions.begin(s,r.draftRevision());jdbc.update("UPDATE un_module_permission SET deleted_at=?,deleted_by=?,updated_revision=?,updated_at=?,updated_by=?,version=version+1 WHERE system_id=? AND resource_type='ACTION' AND resource_id=? AND deleted_at IS NULL",LocalDateTime.now(),s.accountId(),rev.next(),LocalDateTime.now(),s.accountId(),s.systemId(),id);updated(softDelete("un_module_action",s,id,version(r.version()),rev.next()));revisions.finish(s,rev);changed(s,"ACTION",id,"MODULE_ACTION_DELETE",before,null,rev.next(),c.requestId(),c);return new ConfigViews.RevisionResult(Long.toString(rev.next()));}

    public List<ConfigViews.Rule> rules(long systemId,long moduleId){requireModule(systemId,moduleId);return jdbc.query("SELECT * FROM un_module_rule WHERE system_id=? AND module_id=? AND deleted_at IS NULL ORDER BY priority,id",(rs,n)->rule(rs),systemId,moduleId);}
    @Transactional public ConfigViews.Rule createRule(ConfigSession s,long moduleId,ConfigRequests.CreateRule r,String key,RequestContext c){return mutations.idempotent(s.systemId()+":module:"+moduleId+":rule:create",key,r,ConfigViews.Rule.class,()->createRuleNow(s,moduleId,r,key,c));}
    private ConfigViews.Rule createRuleNow(ConfigSession s,long moduleId,ConfigRequests.CreateRule r,String key,RequestContext c){code(r.code());requireModule(s.systemId(),moduleId);validateRuleRefs(s.systemId(),moduleId,r.type(),r.condition(),r.effects());var json=validator.rule(r.condition(),r.effects());var rev=revisions.begin(s,r.draftRevision());var id=ids.nextId();var now=LocalDateTime.now();insert(()->jdbc.update("INSERT INTO un_module_rule (id,system_id,module_id,rule_code,rule_name,rule_type,priority,condition_json,effect_json,desired_status,created_revision,updated_revision,created_at,created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",id,s.systemId(),moduleId,normCode(r.code()),r.name().trim(),r.type().name(),r.priority(),json.conditionJson(),json.effectJson(),r.status().name(),rev.next(),rev.next(),now,s.accountId(),now,s.accountId()));replaceRuleRefs(s,moduleId,id,r.condition(),r.effects(),rev.next(),now);revisions.finish(s,rev);var after=requireRule(s.systemId(),moduleId,id);changed(s,"RULE",id,"MODULE_RULE_CREATE",null,after,rev.next(),key,c);return after;}
    @Transactional public ConfigViews.Rule updateRule(ConfigSession s,long moduleId,long id,ConfigRequests.UpdateRule r,RequestContext c){code(r.code());var before=requireRule(s.systemId(),moduleId,id);requirePublishedCodeUnchanged("un_module_rule","rule_code",s.systemId(),id,normCode(r.code()));validateRuleRefs(s.systemId(),moduleId,r.type(),r.condition(),r.effects());var json=validator.rule(r.condition(),r.effects());var rev=revisions.begin(s,r.draftRevision());updated(jdbc.update("UPDATE un_module_rule SET rule_code=?,rule_name=?,rule_type=?,priority=?,condition_json=?,effect_json=?,desired_status=?,updated_revision=?,updated_at=?,updated_by=?,version=version+1 WHERE id=? AND system_id=? AND module_id=? AND deleted_at IS NULL AND version=?",normCode(r.code()),r.name().trim(),r.type().name(),r.priority(),json.conditionJson(),json.effectJson(),r.status().name(),rev.next(),LocalDateTime.now(),s.accountId(),id,s.systemId(),moduleId,version(r.version())));replaceRuleRefs(s,moduleId,id,r.condition(),r.effects(),rev.next(),LocalDateTime.now());revisions.finish(s,rev);var after=requireRule(s.systemId(),moduleId,id);changed(s,"RULE",id,"MODULE_RULE_UPDATE",before,after,rev.next(),c.requestId(),c);return after;}
    @Transactional public ConfigViews.RevisionResult deleteRule(ConfigSession s,long moduleId,long id,ConfigRequests.DeleteResource r,RequestContext c){var before=requireRule(s.systemId(),moduleId,id);var rev=revisions.begin(s,r.draftRevision());jdbc.update("DELETE FROM un_module_config_reference WHERE system_id=? AND source_type='RULE' AND source_id=?",s.systemId(),id);updated(softDelete("un_module_rule",s,id,version(r.version()),rev.next()));revisions.finish(s,rev);changed(s,"RULE",id,"MODULE_RULE_DELETE",before,null,rev.next(),c.requestId(),c);return new ConfigViews.RevisionResult(Long.toString(rev.next()));}

    private void insertDefaultPage(ConfigSession s,long moduleId,PageType type,long rev,LocalDateTime now){var id=ids.nextId();var code=type.name().toLowerCase(Locale.ROOT);jdbc.update("INSERT INTO un_module_page (id,system_id,module_id,page_code,page_name,page_type,is_default,desired_status,layout_json,created_revision,updated_revision,created_at,created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,1,'ENABLED','{}',?,?,?,?,?,?,0)",id,s.systemId(),moduleId,code,type.name(),type.name(),rev,rev,now,s.accountId(),now,s.accountId());}
    private void insertModulePermissions(ConfigSession s,long moduleId,String moduleCode,String moduleName,long rev,LocalDateTime now){for(var verb:List.of("view","create","update","delete","archive.view","trash.view","history.read","import","export","print")){insertPermission(s,moduleId,moduleId,"MODULE","module."+moduleCode+"."+verb,moduleName+" "+verb,("view".equals(verb)?"MENU":"ACTION"),rev,now);}}
    private void insertPermission(ConfigSession s,long moduleId,long resourceId,String resourceType,String code,String name,String type,long rev,LocalDateTime now){jdbc.update("INSERT INTO un_module_permission (id,system_id,module_id,resource_type,resource_id,permission_code,permission_name,permission_type,desired_status,created_revision,updated_revision,created_at,created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,'ENABLED',?,?,?,?,?,?,0)",ids.nextId(),s.systemId(),moduleId,resourceType,resourceId,code,name,type,rev,rev,now,s.accountId(),now,s.accountId());}
    private void validateFieldCapabilities(
            FieldType type,
            boolean searchable,
            boolean filterable,
            IndexMode indexMode,
            JsonNode properties
    ) {
        if (P4_C4_DERIVED_FIELDS.contains(type)) {
            if (searchable) {
                throw ConfigErrors.invalid(type + " does not support full-text search");
            }
            if (filterable && indexMode == IndexMode.NONE) {
                throw ConfigErrors.invalid(type + " filterable fields require a typed index mode");
            }
            if (indexMode == IndexMode.UNIQUE || indexMode == IndexMode.STATISTIC
                    || type == FieldType.LOOKUP && indexMode == IndexMode.SORT
                    || "BOOLEAN".equals(properties.path("resultSchema").asText())
                    && indexMode == IndexMode.SORT) {
                throw ConfigErrors.invalid(type + " index mode is incompatible with its derived result");
            }
            return;
        }
        if (P4_C2_FIELDS.contains(type)) {
            validateP4C2FieldCapabilities(type, searchable, filterable, indexMode);
            return;
        }
        if (!P4_C1_SCALAR_FIELDS.contains(type) && !P4_C1_COLLECTION_FIELDS.contains(type)) {
            return;
        }
        if (searchable) {
            throw ConfigErrors.invalid(type + " does not support full-text search in P4-C1");
        }
        if (filterable && indexMode == IndexMode.NONE) {
            throw ConfigErrors.invalid(type + " filterable fields require an active index mode");
        }
        if (P4_C1_COLLECTION_FIELDS.contains(type)
                && indexMode != IndexMode.NONE && indexMode != IndexMode.FILTER) {
            throw ConfigErrors.invalid(type + " supports only NONE or FILTER index mode");
        }
        if (P4_C1_SCALAR_FIELDS.contains(type) && indexMode == IndexMode.STATISTIC) {
            throw ConfigErrors.invalid(type + " supports NONE, FILTER, SORT or UNIQUE index mode");
        }
    }

    private static void validateDerivedEnvelope(FieldType type, boolean required, boolean readonly) {
        if (P4_C4_DERIVED_FIELDS.contains(type) && (required || !readonly)) {
            throw ConfigErrors.invalid(type + " fields must be readonly and non-required");
        }
    }

    private static void validateP4C2FieldCapabilities(
            FieldType type,
            boolean searchable,
            boolean filterable,
            IndexMode indexMode
    ) {
        if (searchable && type != FieldType.RICH_TEXT) {
            throw ConfigErrors.invalid(type + " does not support full-text search in P4-C2");
        }
        if ((searchable || filterable) && indexMode == IndexMode.NONE) {
            throw ConfigErrors.invalid(type + " query capabilities require an active index mode");
        }
        if (indexMode == IndexMode.STATISTIC) {
            throw ConfigErrors.invalid(type + " does not support STATISTIC index mode");
        }
        if (indexMode == IndexMode.UNIQUE && !P4_C2_UNIQUE_FIELDS.contains(type)) {
            throw ConfigErrors.invalid(type + " does not support UNIQUE index mode");
        }
        if (indexMode == IndexMode.SORT && !P4_C2_SORTABLE_FIELDS.contains(type)) {
            throw ConfigErrors.invalid(type + " is not sortable");
        }
        if (Set.of(FieldType.ADDRESS, FieldType.GEO, FieldType.RICH_TEXT, FieldType.JSON).contains(type)
                && indexMode != IndexMode.NONE && indexMode != IndexMode.FILTER) {
            throw ConfigErrors.invalid(type + " supports only NONE or FILTER index mode");
        }
    }

    private void syncFieldPermissions(
            ConfigSession session,
            ConfigViews.Module module,
            long fieldId,
            String fieldName,
            FieldType type,
            String fieldCode,
            FieldPermissionMode readMode,
            FieldPermissionMode writeMode,
            long revision,
            LocalDateTime now
    ) {
        var desired = new java.util.LinkedHashMap<String, FieldPermissionSpec>();
        desired.put("read", new FieldPermissionSpec(
                FieldPermissionCodes.read(module.code(), fieldCode), fieldName + " read", permissionStatus(readMode)));
        desired.put("write", new FieldPermissionSpec(
                FieldPermissionCodes.write(module.code(), fieldCode), fieldName + " write", permissionStatus(writeMode)));
        desired.put("sensitive.read", new FieldPermissionSpec(
                FieldPermissionCodes.sensitiveRead(module.code(), fieldCode), "Sensitive identity read",
                type == FieldType.IDENTITY ? DesiredStatus.ENABLED : DesiredStatus.ARCHIVED));
        desired.put("sensitive.query", new FieldPermissionSpec(
                FieldPermissionCodes.sensitiveQuery(module.code(), fieldCode), "Sensitive equality query",
                type == FieldType.IDENTITY || type == FieldType.SECRET
                        ? DesiredStatus.ENABLED : DesiredStatus.ARCHIVED));
        var existing = jdbc.query("SELECT id,permission_code FROM un_module_permission "
                        + "WHERE system_id=? AND resource_type='FIELD' AND resource_id=?",
                (result, row) -> Map.entry(result.getLong("id"), result.getString("permission_code")),
                session.systemId(), fieldId);
        var byKind = new java.util.HashMap<String, Long>();
        existing.forEach(permission -> {
            var kind = fieldPermissionKind(permission.getValue());
            if (kind != null) byKind.put(kind, permission.getKey());
        });
        for (var entry : desired.entrySet()) {
            var specification = entry.getValue();
            var existingId = byKind.remove(entry.getKey());
            if (existingId == null) {
                if (specification.status() != DesiredStatus.ARCHIVED) {
                    insertFieldPermission(session, Long.parseLong(module.id()), fieldId, specification,
                            revision, now);
                }
            } else {
                jdbc.update("UPDATE un_module_permission SET permission_code=?,permission_name=?,permission_type='FIELD',"
                                + "desired_status=?,deleted_at=NULL,deleted_by=NULL,updated_revision=?,updated_at=?,"
                                + "updated_by=?,version=version+1 WHERE id=? AND system_id=?",
                        specification.code(), specification.name(), specification.status().name(), revision, now,
                        session.accountId(), existingId, session.systemId());
            }
        }
    }

    private void insertFieldPermission(ConfigSession session, long moduleId, long fieldId,
                                       FieldPermissionSpec specification, long revision, LocalDateTime now) {
        jdbc.update("INSERT INTO un_module_permission (id,system_id,module_id,resource_type,resource_id,permission_code,"
                        + "permission_name,permission_type,desired_status,created_revision,updated_revision,created_at,"
                        + "created_by,updated_at,updated_by,version) VALUES (?,?,?,'FIELD',?,?,?,'FIELD',?,?,?,?,?,?,?,0)",
                ids.nextId(), session.systemId(), moduleId, fieldId, specification.code(), specification.name(),
                specification.status().name(), revision, revision, now, session.accountId(), now, session.accountId());
    }

    static FieldPermissionMode createPermissionMode(FieldPermissionMode requested) {
        return requested == null ? FieldPermissionMode.INHERIT : requested;
    }

    static FieldPermissionMode updatePermissionMode(FieldPermissionMode requested, FieldPermissionMode current) {
        return requested == null ? current : requested;
    }

    static DesiredStatus permissionStatus(FieldPermissionMode mode) {
        return switch (mode) {
            case INHERIT -> DesiredStatus.ARCHIVED;
            case STAGED -> DesiredStatus.DISABLED;
            case ENFORCED -> DesiredStatus.ENABLED;
        };
    }

    static FieldPermissionMode permissionMode(String status) {
        if (DesiredStatus.ENABLED.name().equals(status)) return FieldPermissionMode.ENFORCED;
        if (DesiredStatus.DISABLED.name().equals(status)) return FieldPermissionMode.STAGED;
        return FieldPermissionMode.INHERIT;
    }

    static boolean supportsWritePermission(FieldType type, boolean readonly) {
        return !readonly && type != FieldType.REFERENCE && !P4_C4_DERIVED_FIELDS.contains(type)
                && !SYSTEM_COMPUTED_FIELDS.contains(type);
    }

    private static void validateFieldPermissionModes(
            FieldType type, boolean readonly, FieldPermissionMode writePermissionMode) {
        if (writePermissionMode != FieldPermissionMode.INHERIT && !supportsWritePermission(type, readonly)) {
            throw ConfigErrors.invalid(
                    "writePermissionMode must be INHERIT for readonly, reference, derived and system fields");
        }
    }

    private static String fieldPermissionKind(String code) {
        if (code.endsWith(".sensitive.read")) return "sensitive.read";
        if (code.endsWith(".sensitive.query")) return "sensitive.query";
        if (code.endsWith(".read")) return "read";
        if (code.endsWith(".write")) return "write";
        return null;
    }

    private record FieldPermissionSpec(String code, String name, DesiredStatus status) { }

    private void softDeleteFieldPermissions(ConfigSession session, long fieldId, long revision, LocalDateTime now) {
        jdbc.update("UPDATE un_module_permission SET deleted_at=?,deleted_by=?,updated_revision=?,updated_at=?,"
                        + "updated_by=?,version=version+1 WHERE system_id=? AND resource_type='FIELD' AND resource_id=? "
                        + "AND deleted_at IS NULL",
                now, session.accountId(), revision, now, session.accountId(), session.systemId(), fieldId);
    }

    private void validateFieldRefs(long systemId,long moduleId,FieldType type,String dictionaryId,
                                   String targetModuleId,JsonNode properties){
        var dictionary=nid(dictionaryId,"dictionaryId");
        var targetModule=nid(targetModuleId,"targetModuleId");
        if(ConfigTypes.DICTIONARY_FIELDS.contains(type)){
            if(dictionary==null)throw ConfigErrors.invalidReference(type+" 字段必须关联字典");
            var referencedDictionary = requireDictionary(systemId,dictionary);
            if (type == FieldType.MULTI_SELECT && !Set.of(
                    DictionaryType.LIST, DictionaryType.TAG, DictionaryType.FIELD_OPTION)
                    .contains(referencedDictionary.type())) {
                throw ConfigErrors.invalidReference("MULTI_SELECT requires a LIST, TAG or FIELD_OPTION dictionary");
            }
            if (type == FieldType.CASCADE && !Set.of(DictionaryType.TREE, DictionaryType.CASCADE)
                    .contains(referencedDictionary.type())) {
                throw ConfigErrors.invalidReference("CASCADE requires a TREE or CASCADE dictionary");
            }
        }else if(dictionary!=null)throw ConfigErrors.invalidReference(type+" 字段不能关联字典");
        if(type==FieldType.REFERENCE){
            if(targetModule!=null)throw ConfigErrors.invalidReference("REFERENCE target module is derived from sourceFieldId");
        }else if(type==FieldType.RELATION||type==FieldType.SUBTABLE){
            if(targetModule==null)throw ConfigErrors.invalidReference(type+" 字段必须关联目标模块");
            requireModule(systemId,targetModule);
        }else if(P4_C4_DERIVED_FIELDS.contains(type)){
            if(targetModule!=null)throw ConfigErrors.invalidReference(type+" target module is derived from its dependency");
        }else if(targetModule!=null)throw ConfigErrors.invalidReference(type+" 字段不能关联目标模块");
        var displayField=textId(properties,"displayFieldId");
        if(displayField!=null){
            if(targetModule==null)throw ConfigErrors.invalidReference("displayFieldId 只能用于关联字段");
            requireField(systemId,targetModule,displayField);
        }
        var valueField=textId(properties,"valueFieldId");
        if(valueField!=null){
            if(targetModule==null)throw ConfigErrors.invalidReference("valueFieldId 只能用于关联字段");
            requireField(systemId,targetModule,valueField);
        }
        var sourceField=textId(properties,"sourceFieldId");
        if(sourceField!=null){
            var source=requireField(systemId,moduleId,sourceField);
            if(type==FieldType.REFERENCE){
                if(source.type()!=FieldType.RELATION||source.status()!=DesiredStatus.ENABLED
                        ||source.properties().path("multiple").asBoolean(false)||source.targetModuleId()==null){
                    throw ConfigErrors.invalidReference("REFERENCE sourceFieldId must be an enabled single RELATION");
                }
                var targetField=textId(properties,"targetFieldId");
                if(targetField==null)throw ConfigErrors.invalidReference("REFERENCE requires targetFieldId");
                var target=requireField(systemId,Long.parseLong(source.targetModuleId()),targetField);
                if(target.status()!=DesiredStatus.ENABLED||!Set.of(FieldType.TEXT,FieldType.NUMBER,FieldType.RATING,
                        FieldType.DATE,FieldType.DATETIME,FieldType.SWITCH).contains(target.type())){
                    throw ConfigErrors.invalidReference("REFERENCE targetFieldId must be an enabled supported scalar field");
                }
            }
        }
        if(type==FieldType.SUMMARY||type==FieldType.LOOKUP){
            var relationId=textId(properties,"relationFieldId");
            if(relationId==null)throw ConfigErrors.invalidReference(type+" requires relationFieldId");
            var relation=requireField(systemId,moduleId,relationId);
            if(relation.type()!=FieldType.RELATION||relation.status()!=DesiredStatus.ENABLED
                    ||relation.targetModuleId()==null){
                throw ConfigErrors.invalidReference(type+" relationFieldId must be an enabled RELATION");
            }
            var targetField=textId(properties,"targetFieldId");
            if(targetField!=null){
                var target=requireField(systemId,Long.parseLong(relation.targetModuleId()),targetField);
                if(target.status()!=DesiredStatus.ENABLED){
                    throw ConfigErrors.invalidReference(type+" targetFieldId must be enabled");
                }
            }
        }
        if(type==FieldType.AGGREGATE){
            var subtableId=textId(properties,"subtableFieldId");
            if(subtableId==null)throw ConfigErrors.invalidReference("AGGREGATE requires subtableFieldId");
            var subtable=requireField(systemId,moduleId,subtableId);
            if(subtable.type()!=FieldType.SUBTABLE||subtable.status()!=DesiredStatus.ENABLED){
                throw ConfigErrors.invalidReference("AGGREGATE subtableFieldId must be an enabled SUBTABLE");
            }
        }
        if(type==FieldType.FORMULA||type==FieldType.CALCULATED){
            var dependencies=new LinkedHashSet<Long>();
            collectDerivedAstFields(properties.path("expressionAst"),dependencies);
            for(var dependencyId:dependencies){
                var dependency=requireField(systemId,moduleId,dependencyId);
                if(dependency.status()!=DesiredStatus.ENABLED){
                    throw ConfigErrors.invalidReference(type+" AST dependencies must be enabled");
                }
            }
        }
        for(var id:textIds(properties,"sourceFieldIds")){
            var source=requireField(systemId,moduleId,id);
            if(type==FieldType.AI_FILL&&(source.status()!=DesiredStatus.ENABLED||source.hidden()
                    ||!Set.of(FieldType.TEXT,FieldType.TEXTAREA,FieldType.PHONE,FieldType.EMAIL,
                    FieldType.URL,FieldType.NUMBER,FieldType.PERCENT,FieldType.MONEY,FieldType.DATE,
                    FieldType.DATETIME,FieldType.RADIO,FieldType.RATING,FieldType.PROGRESS,
                    FieldType.BARCODE,FieldType.RICH_TEXT,FieldType.STATUS,FieldType.SWITCH)
                    .contains(source.type()))){
                throw ConfigErrors.invalidReference(
                        "AI_FILL sources must be enabled readable non-secret scalar fields in the same module");
            }
        }
        var parentField=textId(properties,"parentFieldId");
        if(parentField!=null)requireField(systemId,moduleId,parentField);
        var columnFields=textIds(properties,"columnFieldIds");
        if(!columnFields.isEmpty()){
            if(type!=FieldType.SUBTABLE||targetModule==null)throw ConfigErrors.invalidReference("columnFieldIds 只能用于子表字段");
            for(var id:columnFields){
                var column=requireField(systemId,targetModule,id);
                if(column.status()!=DesiredStatus.ENABLED||column.readonly()||Set.of(
                        FieldType.RELATION,FieldType.REFERENCE,FieldType.SUBTABLE,FieldType.FORMULA,
                        FieldType.SUMMARY,FieldType.CALCULATED,FieldType.LOOKUP,FieldType.AGGREGATE,
                        FieldType.AUTO_NUMBER,FieldType.CREATED_BY,FieldType.CREATED_AT,
                        FieldType.UPDATED_BY,FieldType.UPDATED_AT,FieldType.ATTACHMENT,FieldType.IMAGE,
                        FieldType.FILE_GROUP,FieldType.SIGNATURE,FieldType.AI_FILL,FieldType.TENANT
                ).contains(column.type())){
                    throw ConfigErrors.invalidReference("SUBTABLE columns must be enabled writable P4 field types");
                }
            }
        }
        var filter=propertyCondition(properties,"filter");
        if(filter!=null){
            if(targetModule==null)throw ConfigErrors.invalidReference("filter 只能用于关联字段");
            validateConditionRefs(systemId,targetModule,filter);
        }
    }

    private void replaceFieldRefs(ConfigSession s,long ownerModuleId,long fieldId,String dictionaryId,
                                  String targetModuleId,JsonNode properties,long rev,LocalDateTime now){
        jdbc.update("DELETE FROM un_module_config_reference WHERE system_id=? AND source_type='FIELD' AND source_id=?",s.systemId(),fieldId);
        var dictionary=nid(dictionaryId,"dictionaryId");
        var targetModule=nid(targetModuleId,"targetModuleId");
        if(dictionary!=null)reference(s,"FIELD",fieldId,"DICTIONARY",dictionary,"USES_DICTIONARY","dictionaryId",rev,now);
        if(targetModule!=null)reference(s,"FIELD",fieldId,"MODULE",targetModule,"TARGETS_MODULE","targetModuleId",rev,now);
        var displayField=textId(properties,"displayFieldId");
        if(displayField!=null)reference(s,"FIELD",fieldId,"FIELD",displayField,"DISPLAYS_FIELD","properties.displayFieldId",rev,now);
        var valueField=textId(properties,"valueFieldId");
        if(valueField!=null)reference(s,"FIELD",fieldId,"FIELD",valueField,"USES_VALUE_FIELD","properties.valueFieldId",rev,now);
        var sourceFields=new LinkedHashSet<Long>();
        var sourceField=textId(properties,"sourceFieldId");
        if(sourceField!=null)sourceFields.add(sourceField);
        sourceFields.addAll(textIds(properties,"sourceFieldIds"));
        for(var id:sourceFields)reference(s,"FIELD",fieldId,"FIELD",id,"READS_FIELD","properties.sourceFieldIds",rev,now);
        var targetField=textId(properties,"targetFieldId");
        if(targetField!=null)reference(s,"FIELD",fieldId,"FIELD",targetField,"READS_TARGET_FIELD","properties.targetFieldId",rev,now);
        var relationField=textId(properties,"relationFieldId");
        if(relationField!=null)reference(s,"FIELD",fieldId,"FIELD",relationField,"READS_RELATION","properties.relationFieldId",rev,now);
        var subtableField=textId(properties,"subtableFieldId");
        if(subtableField!=null)reference(s,"FIELD",fieldId,"FIELD",subtableField,"READS_SUBTABLE","properties.subtableFieldId",rev,now);
        var derivedAstFields=new LinkedHashSet<Long>();
        collectDerivedAstFields(properties.path("expressionAst"),derivedAstFields);
        for(var id:derivedAstFields){
            reference(s,"FIELD",fieldId,"FIELD",id,"READS_FIELD","properties.expressionAst",rev,now);
        }
        var parentField=textId(properties,"parentFieldId");
        if(parentField!=null)reference(s,"FIELD",fieldId,"FIELD",parentField,"DEFAULTS_FROM_FIELD","properties.parentFieldId",rev,now);
        for(var id:textIds(properties,"columnFieldIds")){
            reference(s,"FIELD",fieldId,"FIELD",id,"DISPLAYS_COLUMN","properties.columnFieldIds",rev,now);
        }
        var filter=propertyCondition(properties,"filter");
        if(filter!=null){
            var filterFields=new LinkedHashSet<Long>();
            collectFields(filter,filterFields);
            for(var id:filterFields)reference(s,"FIELD",fieldId,"FIELD",id,"FILTERS_FIELD","properties.filter",rev,now);
        }
    }

    private static void collectDerivedAstFields(JsonNode node, java.util.Collection<Long> ids) {
        if (node == null || !node.isObject()) return;
        if (node.hasNonNull("fieldId")) {
            try {
                ids.add(Long.parseLong(node.path("fieldId").asText()));
            } catch (NumberFormatException ignored) {
                // StructuredPropertyValidator owns the stable malformed-id response.
            }
            return;
        }
        node.path("args").forEach(argument -> collectDerivedAstFields(argument, ids));
    }

    private ConfigRequests.Condition propertyCondition(JsonNode properties,String key){
        if(properties==null||!properties.hasNonNull(key))return null;
        return validator.readCondition(properties.get(key).toString());
    }

    private void validateActionRefs(long systemId,long moduleId,JsonNode properties){
        var pageId=textId(properties,"targetPageId");
        if(pageId!=null)requirePage(systemId,moduleId,pageId);
    }

    private void replaceActionRefs(ConfigSession s,long actionId,JsonNode properties,long rev,LocalDateTime now){
        jdbc.update("DELETE FROM un_module_config_reference WHERE system_id=? AND source_type='ACTION' AND source_id=?",s.systemId(),actionId);
        var pageId=textId(properties,"targetPageId");
        if(pageId!=null)reference(s,"ACTION",actionId,"PAGE",pageId,"OPENS_PAGE","properties.targetPageId",rev,now);
    }

    private void validateRuleRefs(long systemId,long moduleId,RuleType type,ConfigRequests.Condition condition,
                                  List<ConfigRequests.Effect> effects){
        validateConditionRefs(systemId,moduleId,condition);
        var expected=switch(type){
            case FIELD_VISIBILITY -> RuleEffect.VISIBLE;
            case FIELD_REQUIRED -> RuleEffect.REQUIRED;
            case FIELD_READ_ONLY -> RuleEffect.READ_ONLY;
            case ACTION_ENABLED -> RuleEffect.ACTION_ENABLED;
            case DELETE_ALLOWED -> RuleEffect.DELETE_ALLOWED;
            case APPROVAL_REQUIRED -> RuleEffect.APPROVAL_REQUIRED;
        };
        for(var effect:effects){
            if(effect.effect()!=expected)throw ConfigErrors.invalidReference(type+" 规则只能使用 "+expected+" 效果");
            var requiresTarget=effect.effect()!=RuleEffect.DELETE_ALLOWED&&effect.effect()!=RuleEffect.APPROVAL_REQUIRED;
            if(requiresTarget&&(effect.targetId()==null||effect.targetId().isBlank()))throw ConfigErrors.invalidReference(effect.effect()+" 效果必须选择目标");
            if(!requiresTarget&&effect.targetId()!=null&&!effect.targetId().isBlank())throw ConfigErrors.invalidReference(effect.effect()+" 效果不能选择目标");
            if(requiresTarget){
                var id=ConfigErrors.id(effect.targetId(),"effect.targetId");
                if(effect.effect()==RuleEffect.ACTION_ENABLED)requireAction(systemId,moduleId,id);else requireField(systemId,moduleId,id);
            }
        }
    }

    private void validateConditionRefs(long systemId,long moduleId,ConfigRequests.Condition condition){
        if(condition==null)return;
        if(condition.fieldId()!=null){
            var field=requireField(systemId,moduleId,ConfigErrors.id(condition.fieldId(),"condition.fieldId"));
            if(condition.operator()!=null&&!operators(field.type()).contains(condition.operator())){
                throw ConfigErrors.invalidReference(field.type()+" 字段不支持 "+condition.operator()+" 运算符");
            }
            if(condition.operator()!=null)validateConditionValue(field.type(),condition.operator(),condition.value());
        }
        if(condition.children()!=null)condition.children().forEach(child->validateConditionRefs(systemId,moduleId,child));
    }

    private void validateConditionValue(FieldType type,ConditionOperator operator,JsonNode value){
        if(operator==ConditionOperator.EMPTY||operator==ConditionOperator.NOT_EMPTY||value==null||value.isNull())return;
        var values=new ArrayList<JsonNode>();
        if((operator==ConditionOperator.IN||operator==ConditionOperator.NOT_IN||operator==ConditionOperator.BETWEEN)
                &&value.isArray())value.forEach(values::add);else values.add(value);
        for(var item:values){
            var valid=conditionScalarMatches(type,item);
            if(!valid)throw ConfigErrors.invalidReference(type+" 字段的 "+operator+" 条件值类型无效");
        }
    }

    private boolean conditionScalarMatches(FieldType type,JsonNode value){
        if(value==null||value.isNull())return false;
        if(ConfigTypes.NUMERIC_FIELDS.contains(type))return value.isNumber();
        if(type==FieldType.SWITCH)return value.isBoolean();
        if(type==FieldType.DATE||type==FieldType.DATETIME||type==FieldType.TIME
                ||type==FieldType.CREATED_AT||type==FieldType.UPDATED_AT)return value.isTextual();
        if(type==FieldType.DATE_RANGE||type==FieldType.TIME_RANGE){
            if(value.isTextual())return true;
            if(!value.isArray()||value.size()!=2)return false;
            for(var item:value)if(!item.isTextual())return false;
            return true;
        }
        if(ConfigTypes.TEXT_FIELDS.contains(type)||ConfigTypes.DICTIONARY_FIELDS.contains(type))return value.isTextual();
        if(type==FieldType.MEMBER||type==FieldType.DEPARTMENT||type==FieldType.TENANT
                ||type==FieldType.RELATION||type==FieldType.REFERENCE||type==FieldType.SUBTABLE
                ||type==FieldType.LOOKUP||type==FieldType.CREATED_BY||type==FieldType.UPDATED_BY){
            return value.isTextual()||value.isIntegralNumber();
        }
        if(type==FieldType.JSON)return true;
        return value.isValueNode()&&!value.isNull();
    }
    private Set<ConditionOperator> operators(FieldType type){
        var result=new LinkedHashSet<>(Set.of(ConditionOperator.EQ,ConditionOperator.NE,ConditionOperator.IN,
                ConditionOperator.NOT_IN,ConditionOperator.EMPTY,ConditionOperator.NOT_EMPTY));
        if(ConfigTypes.TEXT_FIELDS.contains(type)||ConfigTypes.DICTIONARY_FIELDS.contains(type))result.add(ConditionOperator.CONTAINS);
        if(ConfigTypes.ORDERED_FIELDS.contains(type))result.addAll(Set.of(ConditionOperator.GT,ConditionOperator.GTE,
                ConditionOperator.LT,ConditionOperator.LTE,ConditionOperator.BETWEEN));
        return result;
    }

    private Long textId(JsonNode properties,String key){
        if(properties==null||!properties.hasNonNull(key))return null;
        var value=properties.get(key);
        if(!value.isTextual()||value.textValue().isBlank())throw ConfigErrors.invalidReference("properties."+key+" 必须是资源 ID");
        return ConfigErrors.id(value.textValue(),"properties."+key);
    }

    private Set<Long> textIds(JsonNode properties,String key){
        if(properties==null||!properties.hasNonNull(key))return Set.of();
        var value=properties.get(key);
        if(!value.isArray())throw ConfigErrors.invalidReference("properties."+key+" 必须是资源 ID 数组");
        var result=new LinkedHashSet<Long>();
        for(var item:value){
            if(!item.isTextual()||item.textValue().isBlank())throw ConfigErrors.invalidReference("properties."+key+" 包含无效资源 ID");
            result.add(ConfigErrors.id(item.textValue(),"properties."+key));
        }
        return result;
    }

    private void replaceRuleRefs(ConfigSession s,long moduleId,long ruleId,ConfigRequests.Condition condition,List<ConfigRequests.Effect> effects,long rev,LocalDateTime now){jdbc.update("DELETE FROM un_module_config_reference WHERE system_id=? AND source_type='RULE' AND source_id=?",s.systemId(),ruleId);var fields=new LinkedHashSet<Long>();collectFields(condition,fields);for(var id:fields)reference(s,"RULE",ruleId,"FIELD",id,"READS_FIELD","condition",rev,now);for(var e:effects)if(e.targetId()!=null&&!e.targetId().isBlank())reference(s,"RULE",ruleId,e.effect()==RuleEffect.ACTION_ENABLED?"ACTION":"FIELD",ConfigErrors.id(e.targetId(),"effect.targetId"),"AFFECTS_RESOURCE","effects",rev,now);}
    private void collectFields(ConfigRequests.Condition c,java.util.Collection<Long> ids){if(c.fieldId()!=null)ids.add(ConfigErrors.id(c.fieldId(),"condition.fieldId"));if(c.children()!=null)c.children().forEach(h->collectFields(h,ids));}
    private void validateComponentRefs(long systemId,long moduleId,long pageId,Long componentId,Long parentId,Long fieldId,ComponentType type,com.fasterxml.jackson.databind.JsonNode properties){
        if(parentId!=null){requireComponent(systemId,pageId,parentId);var cursor=parentId;var hops=0;while(cursor!=null){if(Objects.equals(cursor,componentId))throw ConfigErrors.invalidReference("组件父子关系不能形成环");if(++hops>1000)throw ConfigErrors.invalidReference("组件层级过深");cursor=jdbc.query("SELECT parent_component_id FROM un_module_page_component WHERE system_id=? AND page_id=? AND id=? AND deleted_at IS NULL",rs->{if(!rs.next())throw ConfigErrors.notFound();var value=rs.getLong(1);return rs.wasNull()?null:value;},systemId,pageId,cursor);}}
        if(fieldId!=null)requireField(systemId,moduleId,fieldId);
        var actionId=componentActionId(type,properties);if(actionId!=null)requireAction(systemId,moduleId,actionId);
    }
    private void replaceComponentRefs(ConfigSession s,long componentId,Long fieldId,ComponentType type,com.fasterxml.jackson.databind.JsonNode properties,long moduleId,long rev,LocalDateTime now){jdbc.update("DELETE FROM un_module_config_reference WHERE system_id=? AND source_type='COMPONENT' AND source_id=?",s.systemId(),componentId);if(fieldId!=null)reference(s,"COMPONENT",componentId,"FIELD",fieldId,"DISPLAYS_FIELD","fieldId",rev,now);var actionId=componentActionId(type,properties);if(actionId!=null){requireAction(s.systemId(),moduleId,actionId);reference(s,"COMPONENT",componentId,"ACTION",actionId,"TRIGGERS_ACTION","properties.actionId",rev,now);}}
    private Long componentActionId(ComponentType type,com.fasterxml.jackson.databind.JsonNode properties){var node=properties==null?null:properties.get("actionId");if(type==ComponentType.ACTION){if(node==null||!node.isTextual()||node.textValue().isBlank())throw ConfigErrors.invalidReference("ACTION 组件必须配置 properties.actionId");return ConfigErrors.id(node.textValue(),"properties.actionId");}if(node!=null)throw ConfigErrors.invalidReference("只有 ACTION 组件可以配置 properties.actionId");return null;}
    private void reference(ConfigSession s,String sourceType,long sourceId,String targetType,long targetId,String relation,String path,long rev,LocalDateTime now){jdbc.update("INSERT INTO un_module_config_reference (id,system_id,source_type,source_id,target_type,target_id,relation_type,property_path,created_revision,created_at,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?)",ids.nextId(),s.systemId(),sourceType,sourceId,targetType,targetId,relation,path,rev,now,s.accountId());}
    private void requireNoFilterScenarioReference(long systemId, long moduleId, String fieldCode) {
        var referenced = jdbc.query("SELECT layout_json FROM un_module_page WHERE system_id=? AND module_id=? "
                        + "AND deleted_at IS NULL",
                result -> {
                    while (result.next()) {
                        if (validator.layoutReferencesField(result.getString("layout_json"), fieldCode)) return true;
                    }
                    return false;
                }, systemId, moduleId);
        if (Boolean.TRUE.equals(referenced)) {
            throw ConfigErrors.invalidReference(
                    "Field is referenced by a shared filter scenario; update or remove the scenario first");
        }
    }
    private long referenceCount(long systemId,String type,long id){return jdbc.queryForObject("SELECT COUNT(*) FROM un_module_config_reference WHERE system_id=? AND target_type=? AND target_id=?",Long.class,systemId,type,id);}
    private void requirePublishedCodeUnchanged(String table,String column,long systemId,long id,String requested){var row=jdbc.queryForMap("SELECT "+column+" code,code_locked_at FROM "+table+" WHERE system_id=? AND id=? AND deleted_at IS NULL",systemId,id);if(row.get("code_locked_at")!=null&&!Objects.equals(String.valueOf(row.get("code")),requested))throw ConfigErrors.invalid("已发布资源的编码不能修改");}
    private int softDelete(String table,ConfigSession s,long id,long version,long rev){var now=LocalDateTime.now();return jdbc.update("UPDATE "+table+" SET deleted_at=?,deleted_by=?,updated_revision=?,updated_at=?,updated_by=?,version=version+1 WHERE id=? AND system_id=? AND deleted_at IS NULL AND version=?",now,s.accountId(),rev,now,s.accountId(),id,s.systemId(),version);}
    private long count(String table,String column,long systemId,long id){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE system_id=? AND "+column+"=? AND deleted_at IS NULL",Long.class,systemId,id);}
    private String itemPath(long systemId,long dictionaryId,String id){return jdbc.queryForObject("SELECT depth_path FROM un_module_dictionary_item WHERE system_id=? AND dictionary_id=? AND id=?",String.class,systemId,dictionaryId,Long.parseLong(id));}
    private void validateTreeParent(DictionaryType type,ConfigViews.DictionaryItem parent){if(parent!=null&&type!=DictionaryType.TREE&&type!=DictionaryType.CASCADE)throw ConfigErrors.invalidReference("只有 TREE/CASCADE 字典允许父子项");}
    private void validateDefault(boolean value,DesiredStatus status){if(value&&status!=DesiredStatus.ENABLED)throw ConfigErrors.invalid("默认字典项必须为 ENABLED");}
    private ConfigViews.Group requireGroup(long systemId,long id){return one("SELECT * FROM un_module_group WHERE system_id=? AND id=? AND deleted_at IS NULL",ConfigDraftService::group,systemId,id);}
    private ConfigViews.Module requireModule(long systemId,long id){return one("SELECT * FROM un_module_definition WHERE system_id=? AND id=? AND deleted_at IS NULL",ConfigDraftService::module,systemId,id);}
    private ConfigViews.Dictionary requireDictionary(long systemId,long id){return one("SELECT * FROM un_module_dictionary WHERE system_id=? AND id=? AND deleted_at IS NULL",ConfigDraftService::dictionary,systemId,id);}
    private ConfigViews.DictionaryItem requireItem(long systemId,long dictionaryId,long id){return one("SELECT * FROM un_module_dictionary_item WHERE system_id=? AND dictionary_id=? AND id=? AND deleted_at IS NULL",ConfigDraftService::item,systemId,dictionaryId,id);}
    private ConfigViews.Field requireField(long systemId,long moduleId,long id){return one(FIELD_SELECT + "WHERE f.system_id=? AND f.module_id=? AND f.id=? AND f.deleted_at IS NULL",(rs,n)->fieldRow(rs),systemId,moduleId,id);}
    private ConfigViews.Page requirePage(long systemId,long moduleId,long id){return one("SELECT * FROM un_module_page WHERE system_id=? AND module_id=? AND id=? AND deleted_at IS NULL",(rs,n)->pageRow(rs),systemId,moduleId,id);}
    private ConfigViews.Component requireComponent(long systemId,long pageId,long id){return one("SELECT * FROM un_module_page_component WHERE system_id=? AND page_id=? AND id=? AND deleted_at IS NULL",(rs,n)->componentRow(rs),systemId,pageId,id);}
    private ConfigViews.Action requireAction(long systemId,long moduleId,long id){return one("SELECT * FROM un_module_action WHERE system_id=? AND module_id=? AND id=? AND deleted_at IS NULL",(rs,n)->actionRow(rs),systemId,moduleId,id);}
    private ConfigViews.Rule requireRule(long systemId,long moduleId,long id){return one("SELECT * FROM un_module_rule WHERE system_id=? AND module_id=? AND id=? AND deleted_at IS NULL",(rs,n)->rule(rs),systemId,moduleId,id);}
    private <T>T one(String sql,org.springframework.jdbc.core.RowMapper<T> mapper,Object...args){var rows=jdbc.query(sql,mapper,args);if(rows.isEmpty())throw ConfigErrors.notFound();return rows.getFirst();}
    private static ConfigViews.Group group(ResultSet r,int n)throws SQLException{return new ConfigViews.Group(str(r,"id"),r.getString("group_code"),r.getString("group_name"),r.getString("description"),r.getString("icon_key"),r.getInt("sort_order"),DesiredStatus.valueOf(r.getString("desired_status")),str(r,"version"),str(r,"updated_revision"));}
    private static ConfigViews.Module module(ResultSet r,int n)throws SQLException{return new ConfigViews.Module(str(r,"id"),str(r,"group_id"),r.getString("module_code"),r.getString("module_name"),r.getString("description"),r.getString("icon_key"),r.getInt("sort_order"),DesiredStatus.valueOf(r.getString("desired_status")),r.getBoolean("allow_comments"),r.getBoolean("allow_team"),str(r,"version"),str(r,"updated_revision"));}
    private static ConfigViews.Dictionary dictionary(ResultSet r,int n)throws SQLException{return new ConfigViews.Dictionary(str(r,"id"),r.getString("dictionary_code"),r.getString("dictionary_name"),DictionaryType.valueOf(r.getString("dictionary_type")),r.getString("category"),r.getString("description"),DesiredStatus.valueOf(r.getString("desired_status")),str(r,"version"),str(r,"updated_revision"));}
    private static ConfigViews.DictionaryItem item(ResultSet r,int n)throws SQLException{return new ConfigViews.DictionaryItem(str(r,"id"),str(r,"dictionary_id"),nstr(r,"parent_id"),r.getString("item_code"),r.getString("item_label"),r.getString("semantic_key"),r.getString("color_value"),r.getString("icon_key"),r.getInt("sort_order"),r.getInt("depth_level"),r.getBoolean("is_default"),DesiredStatus.valueOf(r.getString("desired_status")),str(r,"version"),str(r,"updated_revision"),List.of());}
    private ConfigViews.Field fieldRow(ResultSet r)throws SQLException{return new ConfigViews.Field(str(r,"id"),str(r,"module_id"),nstr(r,"dictionary_id"),nstr(r,"target_module_id"),r.getString("field_code"),r.getString("field_name"),FieldType.valueOf(r.getString("field_type")),r.getInt("sort_order"),r.getBoolean("is_required"),r.getBoolean("is_hidden"),r.getBoolean("is_readonly"),r.getBoolean("is_searchable"),r.getBoolean("is_filterable"),r.getBoolean("show_in_list"),r.getBoolean("show_in_detail"),IndexMode.valueOf(r.getString("index_mode")),DesiredStatus.valueOf(r.getString("desired_status")),validator.read(r.getString("property_json")),permissionMode(r.getString("read_permission_status")),permissionMode(r.getString("write_permission_status")),str(r,"version"),str(r,"updated_revision"));}
    private ConfigViews.Page pageRow(ResultSet r)throws SQLException{return new ConfigViews.Page(str(r,"id"),str(r,"module_id"),r.getString("page_code"),r.getString("page_name"),PageType.valueOf(r.getString("page_type")),r.getBoolean("is_default"),DesiredStatus.valueOf(r.getString("desired_status")),validator.read(r.getString("layout_json")),str(r,"version"),str(r,"updated_revision"));}
    private ConfigViews.Component componentRow(ResultSet r)throws SQLException{return new ConfigViews.Component(str(r,"id"),str(r,"page_id"),nstr(r,"parent_component_id"),nstr(r,"field_id"),r.getString("component_key"),ComponentType.valueOf(r.getString("component_type")),r.getInt("sort_order"),r.getInt("grid_row"),r.getInt("grid_column"),r.getInt("grid_span"),validator.read(r.getString("property_json")),str(r,"version"),str(r,"updated_revision"));}
    private ConfigViews.Action actionRow(ResultSet r)throws SQLException{return new ConfigViews.Action(str(r,"id"),str(r,"module_id"),r.getString("action_code"),r.getString("action_name"),ActionType.valueOf(r.getString("action_type")),ActionPlacement.valueOf(r.getString("placement")),r.getString("permission_code"),r.getString("confirm_message"),r.getInt("sort_order"),DesiredStatus.valueOf(r.getString("desired_status")),validator.read(r.getString("property_json")),str(r,"version"),str(r,"updated_revision"));}
    private ConfigViews.Rule rule(ResultSet r)throws SQLException{return new ConfigViews.Rule(str(r,"id"),str(r,"module_id"),r.getString("rule_code"),r.getString("rule_name"),RuleType.valueOf(r.getString("rule_type")),r.getInt("priority"),validator.readCondition(r.getString("condition_json")),validator.readEffects(r.getString("effect_json")),DesiredStatus.valueOf(r.getString("desired_status")),str(r,"version"),str(r,"updated_revision"));}
    private static String str(ResultSet r,String c)throws SQLException{return Long.toString(r.getLong(c));}private static String nstr(ResultSet r,String c)throws SQLException{var v=r.getLong(c);return r.wasNull()?null:Long.toString(v);}
    private void changed(ConfigSession s,String type,long id,String action,Object before,Object after,long rev,String dedupe,RequestContext c){mutations.changed(s,type,Long.toString(id),action,before,after,rev,dedupe,c);}
    private static long version(String v){return ConfigErrors.version(v);}private static Long nid(String v,String f){return ConfigErrors.nullableId(v,f);}private static String blank(String v){return v==null||v.isBlank()?null:v.trim();}
    private static void code(String v){if(v==null||!CODE.matcher(v.trim().toLowerCase(Locale.ROOT)).matches())throw ConfigErrors.invalid("code 格式无效");}private static void itemCode(String v){if(v==null||!ITEM_CODE.matcher(v.trim()).matches())throw ConfigErrors.invalid("item code 格式无效");}private static String normCode(String v){return v.trim().toLowerCase(Locale.ROOT);}
    private static void updated(int n){if(n!=1)throw ConfigErrors.versionConflict();}private static void insert(Runnable r){try{r.run();}catch(DataIntegrityViolationException e){throw ConfigErrors.conflict("RESOURCE_CONFLICT","编码、默认项或引用与现有配置冲突");}}
}
