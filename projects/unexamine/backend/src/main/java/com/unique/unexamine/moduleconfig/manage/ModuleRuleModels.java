package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.moduleconfig.base.entity.CfgModuleRule;
import com.unique.unexamine.moduleconfig.base.entity.CfgQueryIndex;
import com.unique.unexamine.moduleconfig.base.entity.CfgQueryIndexField;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ModuleRuleModels {
    private ModuleRuleModels() {
    }

    public record CreateRuleRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,99}") String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = "VALIDATION|VISIBILITY|REQUIRED|EDITABLE|APPROVAL|DELETE_ROLE") String ruleType,
            @NotBlank @Pattern(regexp = "CREATE|UPDATE|DELETE|ALWAYS") String triggerEvent,
            @NotNull JsonNode definition,
            @Size(max = 1000) String message,
            @Min(0) Integer sortOrder) {
    }

    public record UpdateRuleRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = "VALIDATION|VISIBILITY|REQUIRED|EDITABLE|APPROVAL|DELETE_ROLE") String ruleType,
            @NotBlank @Pattern(regexp = "CREATE|UPDATE|DELETE|ALWAYS") String triggerEvent,
            @NotNull JsonNode definition,
            @Size(max = 1000) String message,
            @Min(0) Integer sortOrder,
            @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
            @NotNull @Min(0) Integer version) {
    }

    public record TestRuleRequest(@NotNull JsonNode sampleFields) {
    }

    public record ConditionResult(String field, String operator, boolean matched, JsonNode actual, JsonNode expected) {
    }

    public record RuleTestResult(
            boolean matched,
            String effectType,
            String targetField,
            String message,
            List<ConditionResult> conditions) {
    }

    public record IndexFieldRequest(
            @NotNull Long fieldId,
            @NotNull @Min(0) Integer sortOrder,
            @Pattern(regexp = "ASC|DESC") String sortDirection) {
    }

    public record CreateIndexRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,99}") String code,
            @NotBlank @Size(max = 200) String name,
            @NotNull Boolean uniqueIndex,
            @NotEmpty List<@Valid IndexFieldRequest> fields) {
    }

    public record UpdateIndexRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull Boolean uniqueIndex,
            @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
            @NotEmpty List<@Valid IndexFieldRequest> fields,
            @NotNull @Min(0) Integer version) {
    }

    public record QueryIndexDraft(
            CfgQueryIndex index,
            List<CfgQueryIndexField> fields,
            List<String> fieldCodes,
            String scope,
            String projectionPlan) {
    }

    public record RuleIndexDraft(List<CfgModuleRule> rules, List<QueryIndexDraft> indexes) {
    }
}
