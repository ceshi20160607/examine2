package com.unique.examine.module.manage.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.ai.AiConfigurationArtifactFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.manage.api.ConfigTypes;
import com.unique.examine.module.manage.api.ConfigRequests;
import com.unique.examine.module.manage.api.FieldPermissionCodes;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.manage.service.ConfigDraftService;
import com.unique.examine.module.manage.service.ConfigMutationSupport;
import com.unique.examine.module.manage.service.DraftRevisionCoordinator;
import com.unique.examine.module.manage.service.RequestContext;
import com.unique.examine.module.manage.service.StructuredPropertyValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Performs exactly one revision-CAS mutation for either bounded artifact. */
@Component
public class AiConfigurationArtifactDraftWriter {
    private static final Logger LOG = LoggerFactory.getLogger(
            AiConfigurationArtifactDraftWriter.class);
    private final JdbcTemplate jdbc;
    private final IdService ids;
    private final DraftRevisionCoordinator revisions;
    private final ConfigMutationSupport mutations;
    private final StructuredPropertyValidator validator;
    private final ObjectMapper json;
    private final ConfigDraftService drafts;
    private final AiConfigurationArtifactCanonicalizer canonicalizer;

    @Autowired
    public AiConfigurationArtifactDraftWriter(
            JdbcTemplate jdbc,
            IdService ids,
            DraftRevisionCoordinator revisions,
            ConfigMutationSupport mutations,
            StructuredPropertyValidator validator,
            ObjectMapper json,
            ConfigDraftService drafts,
            AiConfigurationArtifactCanonicalizer canonicalizer) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.mutations = Objects.requireNonNull(mutations, "mutations");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.json = Objects.requireNonNull(json, "json");
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.canonicalizer = Objects.requireNonNull(
                canonicalizer, "canonicalizer");
    }

    AiConfigurationArtifactDraftWriter(
            ConfigDraftService drafts,
            AiConfigurationArtifactCanonicalizer canonicalizer) {
        this.jdbc = null;
        this.ids = null;
        this.revisions = null;
        this.mutations = null;
        this.validator = null;
        this.json = null;
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.canonicalizer = Objects.requireNonNull(
                canonicalizer, "canonicalizer");
    }

    @Transactional
    public AiConfigurationArtifactFacade.ArtifactReadback write(
            AiConfigurationArtifactCommandCodec.Command command,
            ConfigSession session,
            RequestContext request) {
        try {
            return switch (command.operation()) {
                case CONFIG_SELECTION_FIELD_DRAFT ->
                        selection(command, session, request);
                case CONFIG_PAGE_LAYOUT_DRAFT -> page(command, session, request);
                case CONFIG_FILTER_SCENARIO_DRAFT ->
                        filterScenario(command, session, request);
                case CONFIG_FIELD_PERMISSION_STAGE_DRAFT ->
                        fieldPermission(command, session, request);
            };
        } catch (DataIntegrityViolationException failure) {
            LOG.error("AI configuration artifact database mutation conflicted", failure);
            throw new BusinessException(
                    "AI_CONFIG_ARTIFACT_CONFLICT",
                    "The requested configuration artifact conflicts with the draft",
                    HttpStatus.CONFLICT);
        }
    }

    private AiConfigurationArtifactFacade.ArtifactReadback selection(
            AiConfigurationArtifactCommandCodec.Command command,
            ConfigSession session,
            RequestContext request) {
        var preview = Objects.requireNonNull(
                command.selectionField(), "selectionField");
        var draft = preview.draft();
        var revision = revisions.begin(
                session, Long.toString(command.expectedDraftRevision()));
        var next = revision.next();
        var now = LocalDateTime.now();
        var dictionaryId = ids.nextId();
        jdbc.update("INSERT INTO un_module_dictionary "
                        + "(id,system_id,dictionary_code,dictionary_name,dictionary_type,"
                        + "category,description,desired_status,created_revision,updated_revision,"
                        + "created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?, 'FIELD_OPTION',NULL,NULL,'ENABLED',?,?,?,?,?,?,0)",
                dictionaryId, session.systemId(), draft.dictionaryCode(),
                draft.dictionaryName(), next, next, now, session.accountId(),
                now, session.accountId());

        var optionViews = new ArrayList<AiConfigurationArtifactFacade.OptionView>();
        for (var index = 0; index < draft.options().size(); index++) {
            var option = draft.options().get(index);
            var optionId = ids.nextId();
            var sortOrder = index * 10;
            jdbc.update("INSERT INTO un_module_dictionary_item "
                            + "(id,system_id,dictionary_id,parent_id,item_code,item_label,"
                            + "semantic_key,color_value,icon_key,sort_order,depth_level,depth_path,"
                            + "is_default,desired_status,created_revision,updated_revision,"
                            + "created_at,created_by,updated_at,updated_by,version) "
                            + "VALUES (?,?,?,NULL,?,?,?,?,NULL,?,0,?,?, 'ENABLED',?,?,?,?,?,?,0)",
                    optionId, session.systemId(), dictionaryId, option.code(),
                    option.label(), option.semanticKey(), option.color(), sortOrder,
                    "/" + optionId, option.defaultOption(), next, next, now,
                    session.accountId(), now, session.accountId());
            jdbc.update("INSERT INTO un_module_dictionary_item_closure "
                            + "(id,system_id,dictionary_id,ancestor_id,descendant_id,depth,"
                            + "created_at,created_by) VALUES (?,?,?,?,?,0,?,?)",
                    ids.nextId(), session.systemId(), dictionaryId, optionId,
                    optionId, now, session.accountId());
            optionViews.add(new AiConfigurationArtifactFacade.OptionView(
                    Long.toString(optionId), option.code(), option.label(),
                    option.semanticKey(), option.color(), option.defaultOption(),
                    sortOrder, 0));
        }

        var properties = json.createObjectNode();
        if (draft.fieldType()
                == AiConfigurationArtifactFacade.SelectionType.MULTI_SELECT) {
            properties.put("multiple", true);
            if (draft.maxSelections() != null) {
                properties.put("maxSelections", draft.maxSelections());
            }
        } else {
            properties.put("multiple", false);
        }
        var type = draft.fieldType()
                == AiConfigurationArtifactFacade.SelectionType.RADIO
                ? ConfigTypes.FieldType.RADIO : ConfigTypes.FieldType.MULTI_SELECT;
        var propertyJson = validator.field(type, properties);
        var fieldId = ids.nextId();
        jdbc.update("INSERT INTO un_module_field "
                        + "(id,system_id,module_id,dictionary_id,target_module_id,field_code,"
                        + "field_name,field_type,sort_order,is_required,is_hidden,is_readonly,"
                        + "is_searchable,is_filterable,show_in_list,show_in_detail,index_mode,"
                        + "desired_status,property_json,created_revision,updated_revision,created_at,"
                        + "created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,NULL,?,?,?,?,?,0,0,0,0,1,1,'NONE','ENABLED',"
                        + "CAST(? AS JSON),?,?,?,?,?,?,0)",
                fieldId, session.systemId(), command.moduleId(), dictionaryId,
                draft.fieldCode(), draft.fieldName(), type.name(),
                preview.sortOrder(), draft.required(), propertyJson, next, next,
                now, session.accountId(), now, session.accountId());
        jdbc.update("INSERT INTO un_module_config_reference "
                        + "(id,system_id,source_type,source_id,target_type,target_id,relation_type,"
                        + "property_path,created_revision,created_at,created_by) "
                        + "VALUES (?,?,'FIELD',?,'DICTIONARY',?,'USES_DICTIONARY',"
                        + "'dictionaryId',?,?,?)",
                ids.nextId(), session.systemId(), fieldId, dictionaryId,
                next, now, session.accountId());
        revisions.finish(session, revision);

        var selection = new AiConfigurationArtifactFacade.SelectionFieldReadback(
                new AiConfigurationArtifactFacade.DictionaryView(
                        Long.toString(dictionaryId), draft.dictionaryCode(),
                        draft.dictionaryName(), 0),
                List.copyOf(optionViews),
                new AiConfigurationArtifactFacade.SelectionFieldView(
                        Long.toString(fieldId), draft.fieldCode(), draft.fieldName(),
                        draft.fieldType(), draft.required(),
                        Long.toString(dictionaryId), preview.sortOrder(),
                        draft.maxSelections(), 0));
        var result = new AiConfigurationArtifactFacade.ArtifactReadback(
                command.operation(), Long.toString(command.configRootId()),
                Long.toString(command.moduleId()), command.moduleCode(), next,
                selection, null);
        mutations.changed(
                session, "FIELD", Long.toString(fieldId),
                "AI_SELECTION_FIELD_CREATE", null, result, next,
                command.proposalId(), request);
        return result;
    }

    private AiConfigurationArtifactFacade.ArtifactReadback page(
            AiConfigurationArtifactCommandCodec.Command command,
            ConfigSession session,
            RequestContext request) {
        var preview = Objects.requireNonNull(command.pageLayout(), "pageLayout");
        var revision = revisions.begin(
                session, Long.toString(command.expectedDraftRevision()));
        var next = revision.next();
        var currentLayout = currentLayout(command, preview);
        var layoutJson = validator.layout(
                ConfigTypes.PageType.valueOf(preview.pageType().name()),
                preserveFilterScenarios(layout(preview.layout()), currentLayout));
        var updated = jdbc.update("UPDATE un_module_page SET layout_json=CAST(? AS JSON),"
                        + "updated_revision=?,updated_at=?,updated_by=?,version=version+1 "
                        + "WHERE id=? AND system_id=? AND module_id=? AND page_code=? "
                        + "AND page_type=? AND version=? AND deleted_at IS NULL",
                layoutJson, next, LocalDateTime.now(), session.accountId(),
                Long.parseLong(preview.pageId()), session.systemId(),
                command.moduleId(), preview.pageCode(), preview.pageType().name(),
                preview.pageVersion());
        if (updated != 1) throw stale();
        revisions.finish(session, revision);
        var page = new AiConfigurationArtifactFacade.PageLayoutReadback(
                preview.pageId(), preview.pageCode(), preview.pageType(),
                preview.pageVersion() + 1, preview.layout());
        var result = new AiConfigurationArtifactFacade.ArtifactReadback(
                command.operation(), Long.toString(command.configRootId()),
                Long.toString(command.moduleId()), command.moduleCode(), next,
                null, page);
        mutations.changed(
                session, "PAGE", preview.pageId(), "AI_PAGE_LAYOUT_UPDATE",
                null, result, next, command.proposalId(), request);
        return result;
    }

    private AiConfigurationArtifactFacade.ArtifactReadback filterScenario(
            AiConfigurationArtifactCommandCodec.Command command,
            ConfigSession session,
            RequestContext request) {
        var preview = Objects.requireNonNull(
                command.filterScenario(), "filterScenario");
        var current = drafts.pages(command.systemId(), command.moduleId()).stream()
                .filter(value -> value.id().equals(preview.pageId()))
                .findFirst().orElseThrow(AiConfigurationArtifactDraftWriter::stale);
        if (!current.code().equals(preview.pageCode())
                || current.type() != ConfigTypes.PageType.LIST
                || current.status() != ConfigTypes.DesiredStatus.ENABLED
                || Long.parseLong(current.version()) != preview.pageVersion()) {
            throw stale();
        }
        var updated = drafts.updatePage(
                session, command.moduleId(), Long.parseLong(preview.pageId()),
                new ConfigRequests.UpdatePage(
                        current.code(), current.name(), current.type(),
                        current.isDefault(), current.status(),
                        preview.resolvedLayout().deepCopy(), current.version(),
                        Long.toString(command.expectedDraftRevision())),
                request);
        if (!updated.id().equals(preview.pageId())
                || !updated.code().equals(preview.pageCode())
                || updated.type() != ConfigTypes.PageType.LIST
                || Long.parseLong(updated.version()) != preview.pageVersion() + 1
                || !updated.layout().equals(preview.resolvedLayout())) {
            throw new IllegalStateException(
                    "Configuration draft returned a different filter scenario");
        }
        return new AiConfigurationArtifactFacade.ArtifactReadback(
                command.operation(), Long.toString(command.configRootId()),
                Long.toString(command.moduleId()), command.moduleCode(),
                command.expectedDraftRevision() + 1, null, null,
                new AiConfigurationArtifactFacade.FilterScenarioReadback(
                        updated.id(), updated.code(),
                        Long.parseLong(updated.version()),
                        canonicalizer.state(updated.layout())),
                null);
    }

    private AiConfigurationArtifactFacade.ArtifactReadback fieldPermission(
            AiConfigurationArtifactCommandCodec.Command command,
            ConfigSession session,
            RequestContext request) {
        var preview = Objects.requireNonNull(
                command.fieldPermissionStage(), "fieldPermissionStage");
        var current = drafts.fields(command.systemId(), command.moduleId()).stream()
                .filter(value -> value.id().equals(preview.fieldId()))
                .findFirst().orElseThrow(AiConfigurationArtifactDraftWriter::stale);
        if (!current.code().equals(preview.fieldCode())
                || !current.name().equals(preview.fieldName())
                || Long.parseLong(current.version()) != preview.fieldVersion()
                || AiConfigurationArtifactCanonicalizer.mode(
                current.readPermissionMode())
                != preview.expectedReadPermissionMode()
                || AiConfigurationArtifactCanonicalizer.mode(
                current.writePermissionMode())
                != preview.expectedWritePermissionMode()) {
            throw stale();
        }
        var updated = drafts.updateField(
                session, command.moduleId(), Long.parseLong(preview.fieldId()),
                new ConfigRequests.UpdateField(
                        current.dictionaryId(), current.targetModuleId(),
                        current.code(), current.name(), current.type(),
                        current.sortOrder(), current.required(), current.hidden(),
                        current.readonly(), current.searchable(),
                        current.filterable(), current.showInList(),
                        current.showInDetail(), current.indexMode(),
                        current.status(), current.properties().deepCopy(),
                        AiConfigurationArtifactCanonicalizer.mode(
                                preview.readPermissionMode()),
                        AiConfigurationArtifactCanonicalizer.mode(
                                preview.writePermissionMode()),
                        current.version(),
                        Long.toString(command.expectedDraftRevision())),
                request);
        if (!updated.id().equals(preview.fieldId())
                || !updated.code().equals(preview.fieldCode())
                || !updated.name().equals(preview.fieldName())
                || Long.parseLong(updated.version()) != preview.fieldVersion() + 1
                || AiConfigurationArtifactCanonicalizer.mode(
                updated.readPermissionMode()) != preview.readPermissionMode()
                || AiConfigurationArtifactCanonicalizer.mode(
                updated.writePermissionMode()) != preview.writePermissionMode()) {
            throw new IllegalStateException(
                    "Configuration draft returned different field permissions");
        }
        return new AiConfigurationArtifactFacade.ArtifactReadback(
                command.operation(), Long.toString(command.configRootId()),
                Long.toString(command.moduleId()), command.moduleCode(),
                command.expectedDraftRevision() + 1, null, null, null,
                new AiConfigurationArtifactFacade.FieldPermissionStageReadback(
                        updated.id(), updated.code(), updated.name(),
                        Long.parseLong(updated.version()),
                        AiConfigurationArtifactCanonicalizer.mode(
                                updated.readPermissionMode()),
                        AiConfigurationArtifactCanonicalizer.mode(
                                updated.writePermissionMode()),
                        FieldPermissionCodes.read(command.moduleCode(), updated.code()),
                        FieldPermissionCodes.write(command.moduleCode(), updated.code())));
    }

    private ObjectNode currentLayout(
            AiConfigurationArtifactCommandCodec.Command command,
            AiConfigurationArtifactFacade.PageLayoutPreview preview
    ) {
        var rows = jdbc.query(
                "SELECT layout_json FROM un_module_page WHERE id=? AND system_id=? AND module_id=? "
                        + "AND page_code=? AND page_type=? AND version=? AND deleted_at IS NULL",
                (result, row) -> result.getString("layout_json"),
                Long.parseLong(preview.pageId()), command.systemId(), command.moduleId(),
                preview.pageCode(), preview.pageType().name(), preview.pageVersion());
        if (rows.size() != 1) {
            throw stale();
        }
        try {
            var stored = json.readTree(rows.getFirst());
            if (stored == null || !stored.isObject()) {
                throw stale();
            }
            return (ObjectNode) stored;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Stored page layout JSON is invalid", exception);
        }
    }

    static ObjectNode preserveFilterScenarios(ObjectNode proposed, ObjectNode current) {
        for (var key : List.of("filterScenarios", "defaultFilterScenarioCode")) {
            if (current.has(key)) {
                proposed.set(key, current.get(key).deepCopy());
            }
        }
        return proposed;
    }

    private ObjectNode layout(AiConfigurationArtifactFacade.PageLayout value) {
        var result = json.createObjectNode();
        result.put("columns", value.columns());
        result.put("gap", value.gap());
        result.put("labelPosition", value.labelPosition().name());
        result.put("density", value.density().name());
        result.put("stickyActions", value.stickyActions());
        if (value.pageSize() != null) result.put("pageSize", value.pageSize());
        if (value.searchEnabled() != null) {
            result.put("showSearch", value.searchEnabled());
        }
        if (value.filterEnabled() != null) {
            result.put("showFilters", value.filterEnabled());
        }
        var sections = result.putArray("sections");
        for (var section : value.sections()) {
            var item = sections.addObject();
            item.put("code", section.code());
            item.put("title", section.title());
            var fields = item.putArray("fieldCodes");
            section.fieldCodes().forEach(fields::add);
        }
        return result;
    }

    private static BusinessException stale() {
        return new BusinessException(
                "AI_CONFIG_DRAFT_STALE",
                "The configuration draft changed before confirmation",
                HttpStatus.CONFLICT);
    }
}
