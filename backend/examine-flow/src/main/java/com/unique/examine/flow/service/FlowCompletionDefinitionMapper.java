package com.unique.examine.flow.service;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.FlowDraftPreflight;
import com.unique.examine.flow.transport.WebhookDeliveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Maps the HTTP completion-step contract to immutable domain snapshots and
 * performs the production webhook preflight before a definition is stored.
 */
@Service
public final class FlowCompletionDefinitionMapper {
    private static final String SECRET_MASK = "********";

    private final WebhookDeliveryClient webhooks;
    private FlowRequestServiceFactory services;

    public FlowCompletionDefinitionMapper(WebhookDeliveryClient webhooks) {
        this.webhooks = Objects.requireNonNull(webhooks, "webhooks");
    }

    @org.springframework.beans.factory.annotation.Autowired
    void configureServices(FlowRequestServiceFactory services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    public List<ApprovalCompletionStep> create(
            long systemId,
            long tenantId,
            List<FlowRequests.CompletionStep> requests
    ) {
        return map(systemId, tenantId, requests, Map.of(), true);
    }

    public List<ApprovalCompletionStep> revise(
            long systemId,
            long tenantId,
            List<FlowRequests.CompletionStep> requests,
            List<ApprovalCompletionStep> current
    ) {
        var byCode = new LinkedHashMap<String, ApprovalCompletionStep>();
        if (current != null) {
            current.forEach(step -> byCode.put(step.code(), step));
        }
        return map(systemId, tenantId, requests, byCode, false);
    }

    /**
     * Validates every frozen published-version edge and walks those exact
     * edges for cycle/depth diagnostics. Tenant scoping is supplied by the
     * service factory, so a foreign target is indistinguishable from missing.
     */
    public List<FlowDraftPreflight.Issue> preflight(
            long systemId,
            long tenantId,
            long parentDefinitionId,
            List<ApprovalCompletionStep> steps
    ) {
        if (services == null || steps == null || steps.isEmpty()) {
            return List.of();
        }
        var issues = new ArrayList<FlowDraftPreflight.Issue>();
        for (var index = 0; index < steps.size(); index++) {
            var step = steps.get(index);
            if (step.type() != ApprovalCompletionStep.Type.SUBFLOW) {
                continue;
            }
            var path = "/completionSteps/" + index + "/subflow";
            var target = step.subflow();
            try {
                services.forTenant(systemId, tenantId).definitionVersion(
                        target.definitionId(), target.version());
            } catch (RuntimeException failure) {
                issues.add(blocker(
                        "SUBFLOW_TARGET_UNAVAILABLE",
                        path,
                        "Subflow target must be an exact published definition "
                                + "version in the current system and tenant"));
                continue;
            }
            var graphPath = new ArrayList<String>();
            graphPath.add(parentDefinitionId + "(draft)");
            var result = walk(
                    systemId,
                    tenantId,
                    parentDefinitionId,
                    target.definitionId(),
                    target.version(),
                    1,
                    new LinkedHashSet<>(),
                    graphPath
            );
            if (result.cycle()) {
                issues.add(blocker(
                        "SUBFLOW_CYCLE",
                        path,
                        "Subflow cycle detected: "
                                + String.join(" -> ", result.path())));
            } else if (result.depthExceeded()) {
                issues.add(blocker(
                        "SUBFLOW_DEPTH_EXCEEDED",
                        path,
                        "Subflow chain exceeds the maximum depth of 8: "
                                + String.join(" -> ", result.path())));
            }
        }
        return List.copyOf(issues);
    }

    private GraphResult walk(
            long systemId,
            long tenantId,
            long rootDefinitionId,
            long definitionId,
            int version,
            int depth,
            LinkedHashSet<String> visited,
            List<String> path
    ) {
        var edge = definitionId + "@" + version;
        var nextPath = new ArrayList<>(path);
        nextPath.add(edge);
        if (definitionId == rootDefinitionId) {
            return new GraphResult(true, false, boundedPath(nextPath));
        }
        if (depth > 8) {
            return new GraphResult(false, true, boundedPath(nextPath));
        }
        if (!visited.add(edge)) {
            return GraphResult.ready();
        }
        final com.unique.examine.flow.domain.ApprovalDefinitionVersion target;
        try {
            target = services.forTenant(systemId, tenantId)
                    .definitionVersion(definitionId, version);
        } catch (RuntimeException failure) {
            return GraphResult.ready();
        }
        for (var step : target.completionSteps()) {
            if (step.type() != ApprovalCompletionStep.Type.SUBFLOW) {
                continue;
            }
            var result = walk(
                    systemId,
                    tenantId,
                    rootDefinitionId,
                    step.subflow().definitionId(),
                    step.subflow().version(),
                    depth + 1,
                    visited,
                    nextPath
            );
            if (result.cycle() || result.depthExceeded()) {
                return result;
            }
        }
        return GraphResult.ready();
    }

    private static List<String> boundedPath(List<String> values) {
        return values.size() <= 10
                ? List.copyOf(values)
                : List.copyOf(values.subList(0, 10));
    }

    private static FlowDraftPreflight.Issue blocker(
            String code,
            String path,
            String message
    ) {
        return new FlowDraftPreflight.Issue(
                FlowDraftPreflight.Severity.BLOCKER,
                code,
                path,
                message
        );
    }

    private List<ApprovalCompletionStep> map(
            long systemId,
            long tenantId,
            List<FlowRequests.CompletionStep> requests,
            Map<String, ApprovalCompletionStep> current,
            boolean creating
    ) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        try {
            var values = requests.stream()
                    .map(request -> step(
                            systemId, tenantId, request, current, creating))
                    .toList();
            return ApprovalCompletionStep.requireSteps(values);
        } catch (BusinessException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw invalid(failure.getMessage() == null
                    ? "Completion step configuration is invalid"
                    : failure.getMessage());
        }
    }

    private ApprovalCompletionStep step(
            long systemId,
            long tenantId,
            FlowRequests.CompletionStep request,
            Map<String, ApprovalCompletionStep> current,
            boolean creating
    ) {
        if (request == null || request.type() == null) {
            throw invalid("Completion step and type are required");
        }
        final ApprovalCompletionStep.Type type;
        try {
            type = ApprovalCompletionStep.Type.valueOf(
                    request.type().strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException failure) {
            throw invalid(
                    "Completion step type must be EXTERNAL_TASK, WEBHOOK or SUBFLOW");
        }
        if (type == ApprovalCompletionStep.Type.EXTERNAL_TASK) {
            if (request.externalTask() == null
                    || request.webhook() != null
                    || request.subflow() != null) {
                throw invalid(
                        "EXTERNAL_TASK requires only externalTask configuration");
            }
            var config = request.externalTask();
            return ApprovalCompletionStep.externalTask(
                    request.code(),
                    request.name(),
                    new ApprovalCompletionStep.ExternalTask(
                            config.topic(),
                            required(config.leaseSeconds(), "leaseSeconds"),
                            required(config.maxAttempts(), "maxAttempts"),
                            required(
                                    config.resultJsonLimitBytes(),
                                    "resultJsonLimitBytes")
                    )
            ).withParallelGroup(request.parallelGroup())
                    .withCompensation(compensation(
                            systemId, tenantId, request.compensation(),
                            current.get(request.code()), creating));
        }
        if (type == ApprovalCompletionStep.Type.SUBFLOW) {
            if (request.subflow() == null
                    || request.externalTask() != null
                    || request.webhook() != null) {
                throw invalid(
                        "SUBFLOW requires only subflow configuration");
            }
            var definitionId = positiveId(
                    request.subflow().definitionId(),
                    "subflow.definitionId");
            var version = required(
                    request.subflow().version(), "subflow.version");
            if (version < 1) {
                throw invalid("subflow.version must be positive");
            }
            requirePublishedTarget(
                    systemId, tenantId, definitionId, version);
            return ApprovalCompletionStep.subflow(
                    request.code(), request.name(),
                    new ApprovalCompletionStep.Subflow(
                            definitionId, version))
                    .withParallelGroup(request.parallelGroup())
                    .withCompensation(compensation(
                            systemId, tenantId, request.compensation(),
                            current.get(request.code()), creating));
        }
        if (request.webhook() == null
                || request.externalTask() != null
                || request.subflow() != null) {
            throw invalid("WEBHOOK requires only webhook configuration");
        }
        var config = request.webhook();
        var secretRef = secret(
                config.secretRef(),
                current.get(request.code()),
                creating
        );
        var webhook = new ApprovalCompletionStep.Webhook(
                config.url(),
                secretRef,
                required(config.timeoutSeconds(), "timeoutSeconds"),
                required(config.maxAttempts(), "maxAttempts"),
                required(
                        config.baseBackoffSeconds(),
                        "baseBackoffSeconds")
        );
        webhooks.validateConfiguration(
                systemId,
                tenantId,
                new WebhookDeliveryClient.WebhookConfiguration(
                        webhook.url(),
                        webhook.secretRef(),
                        webhook.timeoutSeconds(),
                        webhook.maxAttempts(),
                        webhook.baseBackoffSeconds()
                )
        );
        return ApprovalCompletionStep.webhook(
                        request.code(), request.name(), webhook)
                .withParallelGroup(request.parallelGroup())
                .withCompensation(compensation(
                        systemId, tenantId, request.compensation(),
                        current.get(request.code()), creating));
    }

    private ApprovalCompletionStep.Compensation compensation(
            long systemId,
            long tenantId,
            FlowRequests.Compensation request,
            ApprovalCompletionStep current,
            boolean creating
    ) {
        if (request == null) {
            return null;
        }
        if (request.type() == null) {
            throw invalid("Compensation type is required");
        }
        final ApprovalCompletionStep.Type type;
        try {
            type = ApprovalCompletionStep.Type.valueOf(
                    request.type().strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException failure) {
            throw invalid(
                    "Compensation type must be EXTERNAL_TASK, WEBHOOK or SUBFLOW");
        }
        if (type == ApprovalCompletionStep.Type.EXTERNAL_TASK) {
            if (request.externalTask() == null
                    || request.webhook() != null
                    || request.subflow() != null) {
                throw invalid(
                        "EXTERNAL_TASK compensation requires only externalTask configuration");
            }
            var config = request.externalTask();
            return ApprovalCompletionStep.Compensation.externalTask(
                    new ApprovalCompletionStep.ExternalTask(
                            config.topic(),
                            required(config.leaseSeconds(), "leaseSeconds"),
                            required(config.maxAttempts(), "maxAttempts"),
                            required(
                                    config.resultJsonLimitBytes(),
                                    "resultJsonLimitBytes")));
        }
        if (type == ApprovalCompletionStep.Type.SUBFLOW) {
            if (request.subflow() == null
                    || request.externalTask() != null
                    || request.webhook() != null) {
                throw invalid(
                        "SUBFLOW compensation requires only subflow configuration");
            }
            var definitionId = positiveId(
                    request.subflow().definitionId(),
                    "compensation.subflow.definitionId");
            var version = required(
                    request.subflow().version(),
                    "compensation.subflow.version");
            if (version < 1) {
                throw invalid(
                        "compensation.subflow.version must be positive");
            }
            requirePublishedTarget(
                    systemId, tenantId, definitionId, version);
            return ApprovalCompletionStep.Compensation.subflow(
                    new ApprovalCompletionStep.Subflow(
                            definitionId, version));
        }
        if (request.webhook() == null
                || request.externalTask() != null
                || request.subflow() != null) {
            throw invalid(
                    "WEBHOOK compensation requires only webhook configuration");
        }
        var config = request.webhook();
        var secretRef = compensationSecret(
                config.secretRef(), current, creating);
        var webhook = new ApprovalCompletionStep.Webhook(
                config.url(),
                secretRef,
                required(config.timeoutSeconds(), "timeoutSeconds"),
                required(config.maxAttempts(), "maxAttempts"),
                required(
                        config.baseBackoffSeconds(),
                        "baseBackoffSeconds"));
        webhooks.validateConfiguration(
                systemId,
                tenantId,
                new WebhookDeliveryClient.WebhookConfiguration(
                        webhook.url(),
                        webhook.secretRef(),
                        webhook.timeoutSeconds(),
                        webhook.maxAttempts(),
                        webhook.baseBackoffSeconds()));
        return ApprovalCompletionStep.Compensation.webhook(webhook);
    }

    private void requirePublishedTarget(
            long systemId,
            long tenantId,
            long definitionId,
            int version
    ) {
        if (services == null) {
            return;
        }
        try {
            services.forTenant(systemId, tenantId)
                    .definitionVersion(definitionId, version);
        } catch (RuntimeException failure) {
            throw invalid(
                    "Subflow target must be an exact published definition version "
                            + "in the current system and tenant");
        }
    }

    private static String secret(
            String requested,
            ApprovalCompletionStep current,
            boolean creating
    ) {
        if (requested == null) {
            return creating ? null : currentSecret(current);
        }
        if (SECRET_MASK.equals(requested)) {
            if (creating) {
                throw invalid(
                        "A masked value cannot create a webhook secret reference");
            }
            return currentSecret(current);
        }
        if (requested.isEmpty()) {
            return null;
        }
        return requested;
    }

    private static String currentSecret(ApprovalCompletionStep current) {
        return current != null
                && current.type() == ApprovalCompletionStep.Type.WEBHOOK
                ? current.webhook().secretRef()
                : null;
    }

    private static String compensationSecret(
            String requested,
            ApprovalCompletionStep current,
            boolean creating
    ) {
        if (requested == null) {
            return creating ? null : currentCompensationSecret(current);
        }
        if (SECRET_MASK.equals(requested)) {
            if (creating) {
                throw invalid(
                        "A masked value cannot create a compensation webhook secret reference");
            }
            return currentCompensationSecret(current);
        }
        return requested.isEmpty() ? null : requested;
    }

    private static String currentCompensationSecret(
            ApprovalCompletionStep current
    ) {
        return current != null
                && current.compensation() != null
                && current.compensation().type()
                == ApprovalCompletionStep.Type.WEBHOOK
                ? current.compensation().webhook().secretRef()
                : null;
    }

    private static int required(Integer value, String field) {
        if (value == null) {
            throw invalid(field + " is required");
        }
        return value;
    }

    private static long positiveId(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException failure) {
            throw invalid(field + " must be a positive integer string");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(
                "FLOW_COMPLETION_STEP_INVALID",
                message,
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    private record GraphResult(
            boolean cycle,
            boolean depthExceeded,
            List<String> path
    ) {
        static GraphResult ready() {
            return new GraphResult(false, false, List.of());
        }
    }
}
