package com.unique.unexamine.flow.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.ConditionResult;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class FlowBindingModels {
    private FlowBindingModels() {
    }

    public record PublishBindingRequest(
            @NotNull Long moduleId,
            Long actionId,
            @NotBlank @Size(max = 64) String triggerEvent,
            Long flowId,
            @NotBlank @Size(max = 64) String executionMode,
            @Min(0) Integer priorityOrder,
            @Size(max = 2000) String conditionExpression,
            JsonNode condition,
            Boolean mutuallyExclusive,
            Boolean replaceExisting) {
    }

    public record ResolveBindingRequest(
            @NotNull Long moduleId,
            Long actionId,
            @NotBlank @Size(max = 64) String triggerEvent,
            Map<String, Object> variables) {
    }

    public record BindingView(
            Long id,
            Long systemId,
            Long ownerTenantId,
            String scopeType,
            Long moduleId,
            Long actionId,
            String triggerEvent,
            Long flowId,
            Long flowVersionId,
            Integer flowVersionNumber,
            String executionMode,
            Integer priorityOrder,
            String conditionExpression,
            JsonNode condition,
            boolean mutuallyExclusive,
            String status,
            Integer version,
            LocalDateTime updatedAt) {
    }

    public record ResolutionView(
            Long resolutionId,
            Long bindingId,
            String source,
            Long sourceTenantId,
            Long requestedTenantId,
            Long flowId,
            Long flowVersionId,
            Integer flowVersionNumber,
            String definitionHash,
            Map<String, Object> definitionSnapshot,
            String executionMode,
            boolean mutuallyExclusive,
            String resolutionReason,
            LocalDateTime resolvedAt) {
    }

    public record BindingList(List<BindingView> currentTenant, List<BindingView> inheritedDefaults) {
    }

    public record ConditionField(String code, String name, String fieldType, String configJson) { }
    public record PreviewConditionRequest(@NotNull Long moduleId, @NotBlank String triggerEvent,
                                          @NotNull JsonNode condition, Map<String, Object> variables) { }
    public record ConditionPreview(boolean matched, List<ConditionResult> conditions) { }
}
