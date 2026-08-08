package com.unique.examine.module.runtime.ai;

import com.unique.examine.core.ai.AiRecordContextFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Module-owned adapter for one bounded AI record context summary. */
@Component
public class AiRecordContextAdapter implements AiRecordContextFacade {
    private static final long UNUSED_ACCOUNT_ID = 0L;

    private final RecordDetailReader records;

    @Autowired
    public AiRecordContextAdapter(RecordRuntimeService records) {
        this(records::detail);
    }

    AiRecordContextAdapter(RecordDetailReader records) {
        this.records = Objects.requireNonNull(records, "records");
    }

    @Override
    @Transactional(readOnly = true)
    public Result summary(Request request) {
        Objects.requireNonNull(request, "request");
        requireView(request);
        var session = new RuntimeSession(
                UNUSED_ACCOUNT_ID,
                request.systemId(),
                request.memberId(),
                request.tenantId(),
                request.effectivePermissions());
        var detail = records.read(
                session, request.moduleCode(), Long.parseLong(request.recordId()));

        // Detail owns the live schema, row-scope and sensitive-value projection.
        // Preserve requested order and copy display values only.
        var authorized = new LinkedHashMap<String, RecordRuntimeViews.FieldValue>();
        detail.values().forEach(value -> authorized.putIfAbsent(value.fieldCode(), value));
        var values = request.outboundFieldCodes().stream()
                .map(authorized::get)
                .filter(Objects::nonNull)
                .map(value -> new DisplayValue(
                        value.fieldCode(), value.displayValue()))
                .toList();

        return new Result(
                request.moduleCode(),
                new AiRecordContextFacade.Record(
                        detail.recordId(), detail.recordNo(), detail.version(),
                        detail.status(), detail.title(), values));
    }

    private static void requireView(Request request) {
        if (!request.effectivePermissions().contains("system.runtime.access")
                || !request.effectivePermissions().contains(
                "module." + request.moduleCode() + ".view")) {
            throw new BusinessException(
                    "PERMISSION_DENIED",
                    "The current member cannot summarize this runtime record",
                    HttpStatus.FORBIDDEN);
        }
    }

    @FunctionalInterface
    interface RecordDetailReader {
        RecordRuntimeViews.RecordDetail read(
                RuntimeSession session, String moduleCode, long recordId);
    }
}
