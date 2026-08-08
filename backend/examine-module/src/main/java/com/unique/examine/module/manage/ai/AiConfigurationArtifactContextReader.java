package com.unique.examine.module.manage.ai;

import com.unique.examine.core.ai.AiConfigurationArtifactFacade;
import com.unique.examine.core.api.EffectivePermissionFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.manage.api.ConfigTypes;
import com.unique.examine.module.manage.api.ConfigViews;
import com.unique.examine.module.manage.service.ConfigDraftService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Live module-only resolver for bounded configuration artifacts. */
@Component
public class AiConfigurationArtifactContextReader {
    private static final Set<String> REQUIRED_PERMISSIONS = Set.of(
            "system.admin.access", "module.config.manage");
    private static final String COMMON_SQL = """
            SELECT r.id AS root_id,r.draft_revision,m.id AS module_id,m.module_code
            FROM un_module_config_root r
            JOIN un_module_definition m
              ON m.system_id=r.system_id AND m.deleted_at IS NULL
            WHERE r.system_id=? AND m.module_code=?
            """;

    private final EffectivePermissionFacade authorization;
    private final JdbcTemplate jdbc;
    private final ConfigDraftService drafts;

    public AiConfigurationArtifactContextReader(
            EffectivePermissionFacade authorization,
            JdbcTemplate jdbc,
            ConfigDraftService drafts) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.drafts = Objects.requireNonNull(drafts, "drafts");
    }

    @Transactional(readOnly = true)
    public CommonSnapshot common(Access access) {
        var live = authorize(access);
        var rows = jdbc.query(COMMON_SQL, (value, row) -> new CommonSnapshot(
                        value.getLong("root_id"), value.getLong("module_id"),
                        value.getString("module_code"),
                        value.getLong("draft_revision"), live.epoch(),
                        live.permissions()),
                access.systemId(), access.moduleCode());
        if (rows.size() != 1) throw moduleUnavailable();
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public SelectionSnapshot selection(
            Access access, String fieldCode, String dictionaryCode) {
        var common = common(access);
        var nextSort = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sort_order),-10)+10 FROM un_module_field "
                        + "WHERE system_id=? AND module_id=? AND deleted_at IS NULL",
                Long.class, access.systemId(), common.moduleId());
        var fieldCount = count(
                "SELECT COUNT(*) FROM un_module_field WHERE system_id=? "
                        + "AND module_id=? AND field_code=? AND deleted_at IS NULL",
                access.systemId(), common.moduleId(), fieldCode);
        var dictionaryCount = count(
                "SELECT COUNT(*) FROM un_module_dictionary WHERE system_id=? "
                        + "AND dictionary_code=? AND deleted_at IS NULL",
                access.systemId(), dictionaryCode);
        return new SelectionSnapshot(
                common, Math.toIntExact(nextSort == null ? 0 : nextSort),
                fieldCount > 0, dictionaryCount > 0);
    }

    @Transactional(readOnly = true)
    public PageSnapshot page(
            Access access, String pageCode, Set<String> fieldCodes) {
        var common = common(access);
        var rows = jdbc.query(
                "SELECT id,page_code,page_type,version FROM un_module_page "
                        + "WHERE system_id=? AND module_id=? AND page_code=? "
                        + "AND deleted_at IS NULL",
                (value, row) -> new PageIdentity(
                        value.getLong("id"), value.getString("page_code"),
                        AiConfigurationArtifactFacade.PageType.valueOf(
                                value.getString("page_type")),
                        value.getLong("version")),
                access.systemId(), common.moduleId(), pageCode);
        if (rows.size() != 1) {
            throw new BusinessException(
                    "AI_CONFIG_PAGE_UNAVAILABLE",
                    "The requested configuration page is unavailable",
                    HttpStatus.NOT_FOUND);
        }
        var existing = new LinkedHashSet<>(jdbc.queryForList(
                "SELECT field_code FROM un_module_field WHERE system_id=? "
                        + "AND module_id=? AND deleted_at IS NULL",
                String.class, access.systemId(), common.moduleId()));
        if (!existing.containsAll(fieldCodes)) {
            throw new BusinessException(
                    "AI_CONFIG_PAGE_FIELD_UNAVAILABLE",
                    "The page layout contains an unavailable module field",
                    HttpStatus.CONFLICT);
        }
        var page = rows.getFirst();
        return new PageSnapshot(
                common, page.id(), page.code(), page.type(), page.version(),
                Set.copyOf(fieldCodes));
    }

    @Transactional(readOnly = true)
    public FilterScenarioSnapshot filterScenario(
            Access access, String pageCode) {
        var common = common(access);
        var page = drafts.pages(access.systemId(), common.moduleId()).stream()
                .filter(value -> value.code().equals(pageCode))
                .findFirst()
                .orElseThrow(AiConfigurationArtifactContextReader::pageUnavailable);
        if (page.status() != ConfigTypes.DesiredStatus.ENABLED) {
            throw pageUnavailable();
        }
        if (page.type() != ConfigTypes.PageType.LIST) {
            throw new BusinessException(
                    "AI_CONFIG_FILTER_PAGE_TYPE_CONFLICT",
                    "Shared filter scenarios require an enabled LIST page",
                    HttpStatus.CONFLICT);
        }
        return new FilterScenarioSnapshot(common, page);
    }

    @Transactional(readOnly = true)
    public FieldPermissionSnapshot fieldPermission(
            Access access, String fieldCode) {
        var common = common(access);
        var field = drafts.fields(access.systemId(), common.moduleId()).stream()
                .filter(value -> value.code().equals(fieldCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "AI_CONFIG_FIELD_UNAVAILABLE",
                        "The requested configuration field is unavailable",
                        HttpStatus.NOT_FOUND));
        return new FieldPermissionSnapshot(common, field);
    }

    private EffectivePermissionFacade.Evaluation authorize(
            Access access) {
        Objects.requireNonNull(access, "access");
        var live = authorization.evaluateSystem(
                access.systemId(), access.tenantId(), access.memberId());
        if (live.epoch() != access.authorizationEpoch()
                || !live.permissions().equals(access.effectivePermissions())) {
            throw new BusinessException(
                    "AI_CONFIG_AUTHORIZATION_STALE",
                    "Configuration authorization changed", HttpStatus.CONFLICT);
        }
        if (!live.permissions().containsAll(REQUIRED_PERMISSIONS)) {
            throw new BusinessException(
                    "AI_CONFIG_PERMISSION_DENIED",
                    "System administration and configuration permissions are required",
                    HttpStatus.FORBIDDEN);
        }
        return live;
    }

    private long count(String sql, Object... arguments) {
        var value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : value;
    }

    private static BusinessException moduleUnavailable() {
        return new BusinessException(
                "AI_CONFIG_MODULE_UNAVAILABLE",
                "The configuration draft module is unavailable",
                HttpStatus.NOT_FOUND);
    }

    private static BusinessException pageUnavailable() {
        return new BusinessException(
                "AI_CONFIG_PAGE_UNAVAILABLE",
                "The requested configuration page is unavailable",
                HttpStatus.NOT_FOUND);
    }

    public record Access(
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            String moduleCode
    ) {
        public Access {
            if (accountId <= 0 || systemId <= 0 || tenantId <= 0
                    || memberId <= 0 || authorizationEpoch <= 0) {
                throw new IllegalArgumentException("Artifact access identity is invalid");
            }
            effectivePermissions = Set.copyOf(Objects.requireNonNull(
                    effectivePermissions, "effectivePermissions"));
            if (moduleCode == null
                    || !moduleCode.matches("^[a-z][a-z0-9_]{1,63}$")) {
                throw new IllegalArgumentException("moduleCode is invalid");
            }
        }
    }

    public record CommonSnapshot(
            long configRootId,
            long moduleId,
            String moduleCode,
            long draftRevision,
            long authorizationEpoch,
            Set<String> effectivePermissions
    ) {
        public CommonSnapshot {
            if (configRootId <= 0 || moduleId <= 0 || draftRevision < 0
                    || authorizationEpoch <= 0) {
                throw new IllegalArgumentException("Artifact snapshot is invalid");
            }
            effectivePermissions = Set.copyOf(effectivePermissions);
        }
    }

    public record SelectionSnapshot(
            CommonSnapshot common,
            int sortOrder,
            boolean fieldCodeExists,
            boolean dictionaryCodeExists
    ) {
        public SelectionSnapshot {
            common = Objects.requireNonNull(common, "common");
            if (sortOrder < 0) throw new IllegalArgumentException("sortOrder is invalid");
        }
    }

    public record PageSnapshot(
            CommonSnapshot common,
            long pageId,
            String pageCode,
            AiConfigurationArtifactFacade.PageType pageType,
            long pageVersion,
            Set<String> fieldCodes
    ) {
        public PageSnapshot {
            common = Objects.requireNonNull(common, "common");
            if (pageId <= 0 || pageVersion < 0) {
                throw new IllegalArgumentException("Page snapshot is invalid");
            }
            pageType = Objects.requireNonNull(pageType, "pageType");
            fieldCodes = Set.copyOf(fieldCodes);
        }
    }

    public record FilterScenarioSnapshot(
            CommonSnapshot common,
            ConfigViews.Page page
    ) {
        public FilterScenarioSnapshot {
            common = Objects.requireNonNull(common, "common");
            page = Objects.requireNonNull(page, "page");
            if (!page.moduleId().equals(Long.toString(common.moduleId()))
                    || page.type() != ConfigTypes.PageType.LIST
                    || page.status() != ConfigTypes.DesiredStatus.ENABLED
                    || page.layout() == null || !page.layout().isObject()) {
                throw new IllegalArgumentException(
                        "Filter scenario snapshot is invalid");
            }
        }
    }

    public record FieldPermissionSnapshot(
            CommonSnapshot common,
            ConfigViews.Field field
    ) {
        public FieldPermissionSnapshot {
            common = Objects.requireNonNull(common, "common");
            field = Objects.requireNonNull(field, "field");
            if (!field.moduleId().equals(Long.toString(common.moduleId()))) {
                throw new IllegalArgumentException(
                        "Field permission snapshot is invalid");
            }
        }
    }

    private record PageIdentity(
            long id, String code,
            AiConfigurationArtifactFacade.PageType type,
            long version) { }
}
