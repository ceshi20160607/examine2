package com.unique.unexamine.flow.manage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class FlowDesignModels {
    private FlowDesignModels() {
    }

    public record CreateFlowRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description) {
    }

    public record NodeInput(
            @NotBlank @Size(max = 100) String nodeKey,
            @NotBlank @Size(max = 64) String nodeType,
            @NotBlank @Size(max = 200) String name,
            @NotNull BigDecimal positionX,
            @NotNull BigDecimal positionY,
            Map<String, Object> assigneePolicy,
            Map<String, Object> formPolicy,
            Map<String, Object> timeoutPolicy,
            Map<String, Object> exceptionPolicy,
            Map<String, Object> config) {
    }

    public record EdgeInput(
            @NotBlank @Size(max = 100) String edgeKey,
            @NotBlank @Size(max = 100) String sourceNodeKey,
            @NotBlank @Size(max = 100) String targetNodeKey,
            String conditionExpression,
            Integer priorityOrder,
            Map<String, Object> config) {
    }

    public record SaveDraftRequest(
            @NotNull Integer expectedVersion,
            @NotEmpty List<@Valid NodeInput> nodes,
            List<@Valid EdgeInput> edges) {
    }

    public record SimulateRequest(Map<String, Object> variables) {
    }

    public record PublishRequest(
            @NotNull Integer expectedDraftRevision,
            @NotBlank @Size(max = 1000) String changeSummary,
            Map<String, Object> simulationVariables) {
    }

    public record Issue(String code, String location, String message) {
    }

    public record NodeView(
            Long id, String nodeKey, String nodeType, String name,
            BigDecimal positionX, BigDecimal positionY,
            Map<String, Object> assigneePolicy, Map<String, Object> formPolicy,
            Map<String, Object> timeoutPolicy, Map<String, Object> exceptionPolicy,
            Map<String, Object> config, Integer version) {
    }

    public record EdgeView(
            Long id, String edgeKey, String sourceNodeKey, String targetNodeKey,
            String conditionExpression, Integer priorityOrder,
            Map<String, Object> config, Integer version) {
    }

    public record VersionView(
            Long id, Integer versionNumber, Integer draftRevision, String definitionHash,
            String changeSummary, Long publishedByAccountId, LocalDateTime publishedAt,
            Map<String, Object> snapshot, SimulationResult simulationResult) {
    }

    public record FlowView(
            Long id, String contextType, Long platformId, Long systemId, Long tenantId,
            String code, String name, String description, Integer draftRevision,
            String status, Integer version, Long currentVersionId,
            List<NodeView> nodes, List<EdgeView> edges, List<VersionView> versions) {
    }

    public record PublicationCheck(
            Long flowId, Integer draftRevision, boolean valid, List<Issue> issues) {
    }

    public record SimulationStep(String nodeKey, String nodeType, String outcome) {
    }

    public record SimulationResult(
            Long flowId, Integer draftRevision, boolean successful,
            List<SimulationStep> steps, List<Issue> issues, Map<String, Object> variables) {
    }

    public record PublicationResult(
            Long flowId, Long versionId, Integer versionNumber, Integer draftRevision,
            String definitionHash, Long previousVersionId, SimulationResult simulation) {
    }
}
