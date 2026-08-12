package com.unique.examine.module.runtime.history;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class RecordHistoryService {
    private final RecordHistoryRepository repository;
    private final RecordHistoryAccess access;

    public RecordHistoryService(
            RecordHistoryRepository repository,
            RecordHistoryAccess access
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.access = Objects.requireNonNull(access, "access");
    }

    @Transactional(readOnly = true)
    public RecordHistoryPage page(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            int page,
            int size
    ) {
        requirePermissions(session, moduleCode);
        validatePage(page, size);
        var visibleFields = access.requireViewAndCurrentProjection(session, moduleCode, recordId);
        var stored = repository.page(
                session.systemId(),
                requiredTenant(session),
                recordId,
                page,
                size);
        var items = stored.items().stream()
                .map(item -> project(item, visibleFields))
                .toList();
        return new RecordHistoryPage(items, stored.page(), stored.size(), stored.total());
    }

    private static RecordHistoryEntry project(
            RecordHistoryEntry history,
            Set<String> visibleFields
    ) {
        var diff = history.diff().stream()
                .filter(item -> item.fieldCode().startsWith("$")
                        || visibleFields.contains(item.fieldCode()))
                .toList();
        return new RecordHistoryEntry(
                history.historyId(),
                history.recordId(),
                history.recordVersion(),
                history.action(),
                history.actorMemberId(),
                history.occurredAt(),
                diff);
    }

    private static void requirePermissions(RuntimeSession session, String moduleCode) {
        Objects.requireNonNull(session, "session");
        var required = List.of(
                "system.runtime.access",
                "module." + moduleCode + ".view",
                "module." + moduleCode + ".history.read");
        if (!session.permissions().containsAll(required)) {
            throw new BusinessException(
                    "PERMISSION_DENIED",
                    "Record history requires runtime, module view, and history permissions",
                    HttpStatus.FORBIDDEN);
        }
    }

    private static long requiredTenant(RuntimeSession session) {
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new BusinessException(
                    "CONTEXT_TENANT_REQUIRED",
                    "Record history requires an active tenant context",
                    HttpStatus.FORBIDDEN);
        }
        return session.tenantId();
    }

    private static void validatePage(int page, int size) {
        if (page < 1) {
            throw new BusinessException(
                    "RECORD_HISTORY_PAGE_INVALID",
                    "page must be positive",
                    HttpStatus.BAD_REQUEST);
        }
        if (size < 1 || size > 100) {
            throw new BusinessException(
                    "RECORD_HISTORY_SIZE_INVALID",
                    "size must be between 1 and 100",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
