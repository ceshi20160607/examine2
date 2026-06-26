package com.unique.examine.messagelog.manage.audit;

import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.messagelog.manage.audit.AuditLogModels.AuditLogDetailVO;
import com.unique.examine.messagelog.manage.audit.AuditLogModels.AuditLogListItemVO;
import com.unique.examine.messagelog.manage.audit.AuditLogModels.AuditLogQueryRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Audit log search service.
 */
@Service
public class AuditLogService {

    /**
     * Search platform logs.
     *
     * @param pageRequest page request
     * @param query query request
     * @return log page
     */
    public PageResult<AuditLogListItemVO> platformLogs(PageRequest pageRequest, AuditLogQueryRequest query) {
        return page(List.of(item("PLATFORM", null, null)), pageRequest);
    }

    /**
     * Search system logs.
     *
     * @param systemId system id
     * @param pageRequest page request
     * @param query query request
     * @return log page
     */
    public PageResult<AuditLogListItemVO> systemLogs(String systemId, PageRequest pageRequest,
                                                     AuditLogQueryRequest query) {
        return page(List.of(item("SYSTEM", systemId, "tenant_default")), pageRequest);
    }

    /**
     * Return one log detail.
     *
     * @param scope log scope
     * @param systemId optional system id
     * @param logId log id
     * @return log detail
     */
    public AuditLogDetailVO detail(String scope, String systemId, String logId) {
        RequestContext context = RequestContext.current();
        return new AuditLogDetailVO(logId, "BUSINESS", scope, systemId, systemId == null ? null : "tenant_default",
                "admin", "record.create", "business", "record_001", "SUCCESS",
                context.requestId(), context.traceId(), "aud_" + context.traceId(), "127.0.0.1", "Browser",
                Map.of("after", Map.of("title", "业务数据")), Map.of("business.secretNote", "MASKED"),
                Map.of("permissionVersion", "perm_20260623_001"), null, List.of("TASK-20260623-001"),
                LocalDateTime.now());
    }

    private PageResult<AuditLogListItemVO> page(List<AuditLogListItemVO> records, PageRequest pageRequest) {
        int pageNo = pageRequest == null || pageRequest.pageNo() <= 0 ? 1 : pageRequest.pageNo();
        int pageSize = pageRequest == null || pageRequest.pageSize() <= 0 ? 20 : pageRequest.pageSize();
        return new PageResult<>(records, pageNo, pageSize, records.size(), false);
    }

    private AuditLogListItemVO item(String scope, String systemId, String tenantId) {
        RequestContext context = RequestContext.current();
        return new AuditLogListItemVO("log_001", "BUSINESS", scope, systemId, tenantId,
                "admin", "record.create", "business", "record_001", "SUCCESS",
                context.requestId(), context.traceId(), "aud_" + context.traceId(), LocalDateTime.now());
    }
}
