package com.unique.examine.app.manage.datasource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class SystemDataSourceModels {

    private SystemDataSourceModels() {
    }

    public record DataSourceQuery(String sourceType, String status, String tenantId, String keyword) {
    }

    public record DataSourceSaveRequest(String sourceCode, String sourceName, String sourceType, String tenantId,
                                        Map<String, Object> connectionConfig, Map<String, Object> authConfig,
                                        Map<String, Object> syncConfig, Map<String, Object> desensitizeConfig,
                                        Integer status, String idempotencyKey) {
    }

    public record DataSourceVO(String dataSourceId, String systemId, String tenantId, String sourceCode,
                               String sourceName, String sourceType, Map<String, Object> connectionConfig,
                               Map<String, Object> authConfig, Map<String, Object> syncConfig,
                               Map<String, Object> desensitizeConfig, Integer status, String publishStatus,
                               String publishedVersion, String lastCheckStatus, String lastCheckTraceId,
                               LocalDateTime lastCheckedAt, OperationMetadata operation,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record DataSourceCheckResult(String dataSourceId, boolean passed, List<DataSourceCheckItem> items,
                                        String traceId, String auditLogId, LocalDateTime checkedAt,
                                        String targetVersion) {
    }

    public record DataSourceCheckItem(String itemCode, String itemName, String level, boolean passed,
                                      String message) {
    }

    public record OperationMetadata(String operation, String idempotencyKey, String traceId, String auditLogId,
                                    String result, String disabledReason, LocalDateTime operatedAt) {
    }
}
