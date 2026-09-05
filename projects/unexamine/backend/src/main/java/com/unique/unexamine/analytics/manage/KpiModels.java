package com.unique.unexamine.analytics.manage;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class KpiModels {
    private KpiModels() {
    }

    public record KpiRequest(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]{1,99}") String code,
            @NotBlank @Size(max = 200) String name,
            @NotNull Long dataSourceVersionId,
            @NotNull @DecimalMin("0") BigDecimal targetValue,
            @NotBlank @Pattern(regexp = "GTE|LTE") String targetOperator,
            @NotBlank @Pattern(regexp = "MONTH|QUARTER|YEAR") String periodType,
            @NotBlank @Pattern(regexp = "PERSON|DEPARTMENT|ROLE") String responsibleType,
            @NotEmpty List<Long> responsibleIds,
            @NotNull Map<String, Object> visibilityPermission,
            @NotNull Map<String, Object> drillPermission,
            boolean reminderEnabled,
            List<Long> reminderRecipientTenantMemberIds,
            @DecimalMin("0") BigDecimal reminderBelowPercent,
            @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status,
            Integer expectedVersion) {
    }

    public record CalculationRequest(
            @NotBlank @Pattern(regexp = "[0-9]{4}(-Q[1-4]|-(0[1-9]|1[0-2]))?") String periodKey) {
    }

    public record ReminderView(Long id, Long todoId, Long messageId, Long recipientTenantMemberId,
                               String status, LocalDateTime sentAt, LocalDateTime acknowledgedAt) {
    }

    public record ResultView(Long id, String periodKey, String dimensionKey,
                             BigDecimal actualValue, BigDecimal targetValue,
                             BigDecimal achievementRate, String status, boolean stale,
                             Map<String, Object> explanation, LocalDateTime calculatedAt,
                             List<Map<String, Object>> drillItems,
                             boolean drillAvailable, List<ReminderView> reminders) {
    }

    public record KpiView(Long id, String code, String name, Long dataSourceVersionId,
                          BigDecimal targetValue, String targetOperator, String periodType,
                          String responsibleType, List<Long> responsibleIds,
                          Map<String, Object> visibilityPermission,
                          Map<String, Object> drillPermission, boolean reminderEnabled,
                          List<Long> reminderRecipientTenantMemberIds,
                          BigDecimal reminderBelowPercent, String status, Integer version,
                          LocalDateTime updatedAt, ResultView latestResult) {
    }

    public record Preview(Long kpiId, Integer kpiVersion, String periodKey,
                          BigDecimal targetValue, BigDecimal actualValue,
                          BigDecimal achievementRate, String status,
                          String targetExplanation, String sourceExplanation,
                          LocalDateTime calculatedAt, ReportModels.Result sourceResult) {
    }

    public record Overview(List<KpiView> kpis) {
    }
}
