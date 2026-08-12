package com.unique.examine.module.runtime.ai;

import com.unique.examine.core.ai.AiRecordPolicyCatalogFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Objects;

/** Module-owned projection of the current runtime record schema for AI policy checks. */
@Component
public class AiRecordPolicyCatalogAdapter
        implements AiRecordPolicyCatalogFacade {
    private static final long UNUSED_ACCOUNT_ID = 0L;

    private final RecordSchemaReader schemas;

    @Autowired
    public AiRecordPolicyCatalogAdapter(RecordRuntimeService records) {
        this(records::schema);
    }

    AiRecordPolicyCatalogAdapter(RecordSchemaReader schemas) {
        this.schemas = Objects.requireNonNull(schemas, "schemas");
    }

    @Override
    @Transactional(readOnly = true)
    public Result catalog(Request request) {
        Objects.requireNonNull(request, "request");
        requireView(request);
        var session = new RuntimeSession(
                UNUSED_ACCOUNT_ID,
                request.systemId(),
                request.memberId(),
                request.tenantId(),
                request.effectivePermissions());
        var schema = schemas.read(session, request.moduleCode());
        var readable = new LinkedHashSet<String>();
        schema.fields().stream()
                .filter(RecordRuntimeViews.FieldCapability::readable)
                .map(RecordRuntimeViews.FieldCapability::fieldCode)
                .forEach(readable::add);
        return new Result(
                request.moduleCode(),
                schema.schemaVersionId(),
                schema.authzEpoch(),
                readable);
    }

    private static void requireView(Request request) {
        var moduleView = "module." + request.moduleCode() + ".view";
        if (!request.effectivePermissions().contains("system.runtime.access")
                || !request.effectivePermissions().contains(moduleView)) {
            throw new BusinessException(
                    "PERMISSION_DENIED",
                    "The current member cannot inspect this runtime module policy",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    @FunctionalInterface
    interface RecordSchemaReader {
        RecordRuntimeViews.RecordSchema read(
                RuntimeSession session,
                String moduleCode);
    }
}
