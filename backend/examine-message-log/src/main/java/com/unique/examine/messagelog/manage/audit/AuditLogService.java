package com.unique.examine.messagelog.manage.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.messagelog.base.entity.AuditBusinessLog;
import com.unique.examine.messagelog.base.service.AuditBusinessLogBaseService;
import com.unique.examine.messagelog.manage.audit.AuditLogModels.AuditLogDetailVO;
import com.unique.examine.messagelog.manage.audit.AuditLogModels.AuditLogListItemVO;
import com.unique.examine.messagelog.manage.audit.AuditLogModels.AuditLogQueryRequest;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Audit log search service backed by persisted business audit logs.
 */
@Service
public class AuditLogService {

    private final AuditBusinessLogBaseService auditBusinessLogBaseService;

    public AuditLogService(AuditBusinessLogBaseService auditBusinessLogBaseService) {
        this.auditBusinessLogBaseService = auditBusinessLogBaseService;
    }

    /**
     * Search platform logs.
     *
     * @param pageRequest page request
     * @param query query request
     * @return log page
     */
    public PageResult<AuditLogListItemVO> platformLogs(PageRequest pageRequest, AuditLogQueryRequest query) {
        return search("PLATFORM", null, pageRequest, query);
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
        return search("SYSTEM", systemId, pageRequest, query);
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
        AuditBusinessLog log = findLog(scope, systemId, logId);
        return new AuditLogDetailVO(String.valueOf(log.getId()), safe(log.getLogType(), "BUSINESS"),
                safe(log.getScope(), scope), stringValue(log.getSystemId()), stringValue(log.getTenantId()),
                stringValue(log.getOperatorId()), safe(log.getActionCode(), "-"), safe(log.getObjectType(), "-"),
                safe(log.getObjectId(), "-"), safe(log.getResult(), "UNKNOWN"), safe(log.getRequestId(), "-"),
                safe(log.getTraceId(), "-"), safe(log.getAuditLogId(), "-"), safe(log.getIp(), "-"),
                safe(log.getDevice(), "-"), rawMap(log.getFieldDiff()), rawMap(log.getDesensitizeResult()),
                rawMap(log.getPermissionSnapshot()), log.getFailureReason(), List.of(), log.getCreatedAt());
    }

    private PageResult<AuditLogListItemVO> search(String scope, String systemId, PageRequest pageRequest,
                                                  AuditLogQueryRequest query) {
        int pageNo = pageRequest == null || pageRequest.pageNo() <= 0 ? 1 : pageRequest.pageNo();
        int pageSize = pageRequest == null || pageRequest.pageSize() <= 0 ? 20 : pageRequest.pageSize();
        LambdaQueryWrapper<AuditBusinessLog> wrapper = new LambdaQueryWrapper<AuditBusinessLog>()
                .eq(AuditBusinessLog::getScope, scope)
                .orderByDesc(AuditBusinessLog::getCreatedAt)
                .orderByDesc(AuditBusinessLog::getId);
        Long resolvedSystemId = parseLong(systemId);
        if (resolvedSystemId != null) {
            wrapper.eq(AuditBusinessLog::getSystemId, resolvedSystemId);
        }
        applyQuery(wrapper, query);
        List<AuditBusinessLog> all = auditBusinessLogBaseService.list(wrapper);
        int fromIndex = Math.min(Math.max(0, (pageNo - 1) * pageSize), all.size());
        int toIndex = Math.min(fromIndex + pageSize, all.size());
        List<AuditLogListItemVO> records = all.subList(fromIndex, toIndex).stream()
                .map(this::toItem)
                .toList();
        return new PageResult<>(records, pageNo, pageSize, all.size(), toIndex < all.size());
    }

    private void applyQuery(LambdaQueryWrapper<AuditBusinessLog> wrapper, AuditLogQueryRequest query) {
        if (query == null) {
            return;
        }
        eqIfPresent(wrapper, AuditBusinessLog::getLogType, query.logType());
        eqIfPresent(wrapper, AuditBusinessLog::getResult, query.result());
        eqIfPresent(wrapper, AuditBusinessLog::getActionCode, query.action());
        eqIfPresent(wrapper, AuditBusinessLog::getObjectType, query.objectType());
        eqIfPresent(wrapper, AuditBusinessLog::getTraceId, query.traceId());
        Long tenantId = parseLong(query.tenantId());
        if (tenantId != null) {
            wrapper.eq(AuditBusinessLog::getTenantId, tenantId);
        }
        Long operatorId = parseLong(query.operator());
        if (operatorId != null) {
            wrapper.eq(AuditBusinessLog::getOperatorId, operatorId);
        }
        String keyword = trim(query.keyword());
        if (keyword != null) {
            wrapper.and(item -> item.like(AuditBusinessLog::getActionCode, keyword)
                    .or().like(AuditBusinessLog::getObjectType, keyword)
                    .or().like(AuditBusinessLog::getObjectId, keyword)
                    .or().like(AuditBusinessLog::getTraceId, keyword)
                    .or().like(AuditBusinessLog::getAuditLogId, keyword));
        }
    }

    private void eqIfPresent(LambdaQueryWrapper<AuditBusinessLog> wrapper,
                             SFunction<AuditBusinessLog, ?> column,
                             String value) {
        String trimmed = trim(value);
        if (trimmed != null) {
            wrapper.eq(column, trimmed);
        }
    }

    private AuditBusinessLog findLog(String scope, String systemId, String logId) {
        Long id = parseLong(logId);
        Long resolvedSystemId = parseLong(systemId);
        LambdaQueryWrapper<AuditBusinessLog> wrapper = new LambdaQueryWrapper<AuditBusinessLog>()
                .eq(AuditBusinessLog::getScope, scope);
        if (id != null) {
            wrapper.eq(AuditBusinessLog::getId, id);
        } else {
            wrapper.eq(AuditBusinessLog::getAuditLogId, logId);
        }
        if (resolvedSystemId != null) {
            wrapper.eq(AuditBusinessLog::getSystemId, resolvedSystemId);
        }
        wrapper.orderByDesc(AuditBusinessLog::getCreatedAt).orderByDesc(AuditBusinessLog::getId);
        AuditBusinessLog log = auditBusinessLogBaseService.getOne(wrapper, false);
        if (log == null) {
            throw new IllegalArgumentException("Audit log not found: " + logId);
        }
        return log;
    }

    private AuditLogListItemVO toItem(AuditBusinessLog log) {
        return new AuditLogListItemVO(String.valueOf(log.getId()), safe(log.getLogType(), "BUSINESS"),
                safe(log.getScope(), "-"), stringValue(log.getSystemId()), stringValue(log.getTenantId()),
                stringValue(log.getOperatorId()), safe(log.getActionCode(), "-"), safe(log.getObjectType(), "-"),
                safe(log.getObjectId(), "-"), safe(log.getResult(), "UNKNOWN"), safe(log.getRequestId(), "-"),
                safe(log.getTraceId(), "-"), safe(log.getAuditLogId(), "-"), log.getCreatedAt());
    }

    private Map<String, Object> rawMap(String value) {
        String trimmed = trim(value);
        if (trimmed == null) {
            return Map.of();
        }
        return Map.of("raw", trimmed);
    }

    private Long parseLong(String value) {
        String trimmed = trim(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String safe(String value, String fallback) {
        String trimmed = trim(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String stringValue(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
