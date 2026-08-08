package com.unique.examine.module.runtime.access;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.api.RuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.ModuleRuntimeService;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Bridges independent feature modules to the existing record-detail VIEW authorization path.
 *
 * <p>The adapter deliberately delegates record visibility to {@link RecordRuntimeService#detail}
 * instead of maintaining a second copy of its tenant and data-scope SQL.</p>
 */
@Component
public class RuntimeRecordAccessAdapter implements RuntimeRecordAccessFacade {
    private static final long UNUSED_ACCOUNT_ID = 0L;

    private final RecordViewReader records;
    private final ModuleDefinitionReader modules;

    @Autowired
    public RuntimeRecordAccessAdapter(
            RecordRuntimeService recordRuntimeService,
            ModuleRuntimeService moduleRuntimeService
    ) {
        this(recordRuntimeService::detail, moduleRuntimeService::definition);
    }

    RuntimeRecordAccessAdapter(RecordViewReader records, ModuleDefinitionReader modules) {
        this.records = Objects.requireNonNull(records, "records");
        this.modules = Objects.requireNonNull(modules, "modules");
    }

    @Override
    @Transactional(readOnly = true)
    public RuntimeRecordAccess requireView(RuntimeRecordAccessRequest request) {
        Objects.requireNonNull(request, "request");
        requireShellAndModuleView(request);
        var session = new RuntimeSession(
                UNUSED_ACCOUNT_ID,
                request.systemId(),
                request.memberId(),
                request.tenantId(),
                request.effectivePermissions());

        // Resolve the record first so module metadata cannot be used to probe an inaccessible record.
        var record = records.read(session, request.moduleCode(), request.recordId());
        var definition = modules.read(session, request.moduleCode());
        return new RuntimeRecordAccess(
                record.recordId(),
                record.version(),
                allowComments(definition));
    }

    private static void requireShellAndModuleView(RuntimeRecordAccessRequest request) {
        var moduleView = "module." + request.moduleCode() + ".view";
        if (!request.effectivePermissions().contains("system.runtime.access")
                || !request.effectivePermissions().contains(moduleView)) {
            throw new BusinessException(
                    "PERMISSION_DENIED",
                    "The current member cannot view this runtime module",
                    HttpStatus.FORBIDDEN);
        }
    }

    private static boolean allowComments(RuntimeViews.Definition definition) {
        var value = definition.module().path("allow_comments");
        return value.isBoolean() ? value.booleanValue() : value.asInt(0) != 0;
    }

    @FunctionalInterface
    interface RecordViewReader {
        RecordRuntimeViews.RecordDetail read(RuntimeSession session, String moduleCode, long recordId);
    }

    @FunctionalInterface
    interface ModuleDefinitionReader {
        RuntimeViews.Definition read(RuntimeSession session, String moduleCode);
    }
}
