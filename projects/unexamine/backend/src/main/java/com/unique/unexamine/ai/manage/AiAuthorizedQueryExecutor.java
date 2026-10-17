package com.unique.unexamine.ai.manage;

import com.unique.unexamine.analytics.manage.ReportModels;
import com.unique.unexamine.analytics.manage.ReportService;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordList;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolates an AI tool invocation from the conversation evidence transaction. A failing business
 * query may roll back its own transaction, while the caller can still persist a non-fabricated
 * degraded result and audit trail.
 */
@Service
public class AiAuthorizedQueryExecutor {
    private final RuntimeDataService runtimeDataService;
    private final ReportService reportService;

    public AiAuthorizedQueryExecutor(RuntimeDataService runtimeDataService, ReportService reportService) {
        this.runtimeDataService = runtimeDataService;
        this.reportService = reportService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RuntimeRecordView detail(
            AuthenticatedContext context, String moduleCode, Long recordId, String traceId) {
        return runtimeDataService.detail(context, moduleCode, recordId, traceId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public RuntimeRecordList list(
            AuthenticatedContext context, String moduleCode, String lifecycleState, String tenantScope,
            String search, String filtersJson, String sortField, String sortDirection,
            int pageNumber, int pageSize, String traceId) {
        return runtimeDataService.list(context, moduleCode, lifecycleState, tenantScope, search, filtersJson,
                sortField, sortDirection, pageNumber, pageSize, traceId);
    }

    /** 报表查询单独开启事务，失败时会话仍能保存明确的降级结果与审计。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReportModels.Result report(AuthenticatedContext context, Long dataSourceId, String traceId) {
        return reportService.executePublished(context, dataSourceId, traceId);
    }
}
