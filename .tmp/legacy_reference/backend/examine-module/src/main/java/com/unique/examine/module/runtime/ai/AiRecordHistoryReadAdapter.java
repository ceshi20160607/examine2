package com.unique.examine.module.runtime.ai;

import com.unique.examine.core.ai.AiRecordHistoryReadFacade;
import com.unique.examine.module.runtime.history.RecordHistoryDiff;
import com.unique.examine.module.runtime.history.RecordHistoryPage;
import com.unique.examine.module.runtime.history.RecordHistoryService;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Module-owned translation from one strict AI request to native projected history. */
@Component
public class AiRecordHistoryReadAdapter implements AiRecordHistoryReadFacade {
    private final HistoryReader histories;

    @Autowired
    public AiRecordHistoryReadAdapter(RecordHistoryService histories) {
        this(histories::page);
    }

    AiRecordHistoryReadAdapter(HistoryReader histories) {
        this.histories = Objects.requireNonNull(histories, "histories");
    }

    @Override
    @Transactional(readOnly = true)
    public Result query(Request request) {
        Objects.requireNonNull(request, "request");
        var session = new RuntimeSession(
                request.accountId(), request.systemId(), request.memberId(),
                request.tenantId(), request.effectivePermissions());
        var page = histories.page(
                session, request.moduleCode(), Long.parseLong(request.recordId()),
                1, request.limit());
        return new Result(
                request.moduleCode(), request.recordId(), page.total(),
                route(request),
                page.items().stream().limit(request.limit()).map(value ->
                        new History(
                                value.historyId(), value.recordVersion(),
                                value.action(), value.actorMemberId(),
                                value.occurredAt(), value.diff().stream()
                                .map(AiRecordHistoryReadAdapter::diff).toList()))
                        .toList());
    }

    private static Diff diff(RecordHistoryDiff value) {
        if (value.masked()) {
            return new Diff(value.fieldCode(), null, null, true);
        }
        return new Diff(
                value.fieldCode(), value.beforeValue().toString(),
                value.afterValue().toString(), false);
    }

    private static String route(Request request) {
        return "/systems/" + request.systemId() + "/workbench?module="
                + request.moduleCode() + "&mode=view&record=" + request.recordId();
    }

    @FunctionalInterface
    interface HistoryReader {
        RecordHistoryPage page(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                int page,
                int size);
    }
}
