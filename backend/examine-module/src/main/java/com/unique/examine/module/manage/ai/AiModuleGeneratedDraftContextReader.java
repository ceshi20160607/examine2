package com.unique.examine.module.manage.ai;

import com.unique.examine.core.ai.AiModuleGeneratedDraftFacade;
import com.unique.examine.core.api.EffectivePermissionFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.manage.security.ConfigPermissions;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.report.domain.ReportActor;
import com.unique.examine.module.report.port.ReportSourceCatalog;
import com.unique.examine.module.runtime.printing.PrintRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;

/** Live configuration authorization and facts for report/print AI drafts. */
@Component
public class AiModuleGeneratedDraftContextReader {
    private static final Set<String> REQUIRED_PERMISSIONS = Set.of(
            ConfigPermissions.SYSTEM_ADMIN_ACCESS,
            ConfigPermissions.MODULE_CONFIG_MANAGE);
    private static final Set<String> FORBIDDEN_PRINT_TYPES = Set.of(
            "IDENTITY", "SECRET", "RELATION", "REFERENCE", "SUBTABLE");

    private final EffectivePermissionFacade authorization;
    private final ReportSourceCatalog sources;
    private final PrintRepository prints;
    private final JdbcTemplate jdbc;

    public AiModuleGeneratedDraftContextReader(
            EffectivePermissionFacade authorization,
            ReportSourceCatalog sources,
            PrintRepository prints,
            JdbcTemplate jdbc) {
        this.authorization = Objects.requireNonNull(
                authorization, "authorization");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.prints = Objects.requireNonNull(prints, "prints");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Transactional(readOnly = true)
    public OwnerContext validate(
            Access access,
            AiModuleGeneratedDraftFacade.ReportDraft report,
            AiModuleGeneratedDraftFacade.PrintTemplateDraft printTemplate) {
        var context = authorize(access);
        validateFacts(context, access.operation(), report, printTemplate);
        return context;
    }

    @Transactional(readOnly = true)
    public OwnerContext authorize(Access access) {
        Objects.requireNonNull(access, "access");
        var live = authorization.evaluateSystem(
                access.systemId(), access.tenantId(), access.memberId());
        if (!live.permissions().containsAll(REQUIRED_PERMISSIONS)) {
            throw new BusinessException(
                    "AI_MODULE_DRAFT_PERMISSION_DENIED",
                    "System administration and module configuration permissions are required",
                    HttpStatus.FORBIDDEN);
        }
        if (live.epoch() != access.authorizationEpoch()
                || !live.permissions().equals(access.effectivePermissions())) {
            throw new BusinessException(
                    "AI_MODULE_DRAFT_AUTHORIZATION_STALE",
                    "Module configuration authorization changed",
                    HttpStatus.CONFLICT);
        }
        return new OwnerContext(
                new ConfigSession(
                        access.accountId(), access.systemId(), access.memberId(),
                        access.tenantId(), live.permissions()),
                new ReportActor(
                        access.systemId(), access.tenantId(), access.memberId()));
    }

    @Transactional(readOnly = true)
    public void validateFacts(
            OwnerContext context,
            AiModuleGeneratedDraftFacade.Operation operation,
            AiModuleGeneratedDraftFacade.ReportDraft report,
            AiModuleGeneratedDraftFacade.PrintTemplateDraft printTemplate) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(operation, "operation");
        if (operation == AiModuleGeneratedDraftFacade.Operation
                .CONFIG_REPORT_DRAFT) {
            validateReport(context.reportActor(),
                    Objects.requireNonNull(report, "report"));
        } else {
            validatePrint(context.configSession(),
                    Objects.requireNonNull(printTemplate, "printTemplate"));
        }
    }

    private void validateReport(
            ReportActor actor,
            AiModuleGeneratedDraftFacade.ReportDraft draft) {
        if (count("SELECT COUNT(*) FROM un_module_report "
                + "WHERE system_id=? AND tenant_id=? AND report_code=?",
                actor.systemId(), actor.tenantId(), draft.code()) != 0) {
            throw stale("AI_MODULE_REPORT_CODE_CONFLICT",
                    "The proposed report code is already used");
        }
        var source = sources.active(
                        actor.systemId(), actor.tenantId(),
                        Long.parseLong(draft.dataSourceId()))
                .orElseThrow(() -> stale(
                        "AI_MODULE_REPORT_SOURCE_UNAVAILABLE",
                        "The proposed report data source is not published or visible"));
        var available = new LinkedHashMap<String, Boolean>();
        source.fields().forEach(field ->
                available.put(field.code(), field.readable()));
        if (draft.outputFieldCodes().stream()
                .anyMatch(code -> !Boolean.TRUE.equals(available.get(code)))) {
            throw stale("AI_MODULE_REPORT_FIELD_UNAVAILABLE",
                    "A proposed report output field is unavailable");
        }
    }

    private void validatePrint(
            ConfigSession session,
            AiModuleGeneratedDraftFacade.PrintTemplateDraft draft) {
        var modules = jdbc.queryForList(
                "SELECT id FROM un_module_definition WHERE system_id=? "
                        + "AND module_code=? AND deleted_at IS NULL",
                Long.class, session.systemId(), draft.moduleCode());
        if (modules.size() != 1) {
            throw stale("AI_MODULE_PRINT_MODULE_UNAVAILABLE",
                    "The proposed print-template module is unavailable");
        }
        var moduleId = modules.getFirst();
        if (count("SELECT COUNT(*) FROM un_module_print_template "
                + "WHERE system_id=? AND logical_module_id=? "
                + "AND template_code=? AND deleted_at IS NULL",
                session.systemId(), moduleId, draft.code()) != 0) {
            throw stale("AI_MODULE_PRINT_CODE_CONFLICT",
                    "The proposed print-template code is already used");
        }
        var available = new LinkedHashMap<String, PrintRepository.FieldRef>();
        prints.draftFields(session.systemId(), moduleId)
                .forEach(field -> available.put(field.code(), field));
        for (var code : draft.fieldCodes()) {
            var field = available.get(code);
            if (field == null || field.hidden()
                    || FORBIDDEN_PRINT_TYPES.contains(field.type())) {
                throw stale("AI_MODULE_PRINT_FIELD_UNAVAILABLE",
                        "A proposed print-template field is unavailable");
            }
        }
    }

    public long printModuleId(
            ConfigSession session,
            AiModuleGeneratedDraftFacade.PrintTemplateDraft draft) {
        var ids = jdbc.queryForList(
                "SELECT id FROM un_module_definition WHERE system_id=? "
                        + "AND module_code=? AND deleted_at IS NULL",
                Long.class, session.systemId(), draft.moduleCode());
        if (ids.size() != 1) {
            throw stale("AI_MODULE_PRINT_MODULE_UNAVAILABLE",
                    "The proposed print-template module is unavailable");
        }
        return ids.getFirst();
    }

    private long count(String sql, Object... arguments) {
        var value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : value;
    }

    private static BusinessException stale(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    public record Access(
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            AiModuleGeneratedDraftFacade.Operation operation
    ) {
        public Access {
            if (accountId <= 0 || systemId <= 0 || tenantId <= 0
                    || memberId <= 0 || authorizationEpoch <= 0) {
                throw new IllegalArgumentException(
                        "Module-generated draft access identity is invalid");
            }
            effectivePermissions = Set.copyOf(Objects.requireNonNull(
                    effectivePermissions, "effectivePermissions"));
            operation = Objects.requireNonNull(operation, "operation");
        }
    }

    public record OwnerContext(
            ConfigSession configSession,
            ReportActor reportActor
    ) {
        public OwnerContext {
            configSession = Objects.requireNonNull(
                    configSession, "configSession");
            reportActor = Objects.requireNonNull(reportActor, "reportActor");
        }
    }
}
