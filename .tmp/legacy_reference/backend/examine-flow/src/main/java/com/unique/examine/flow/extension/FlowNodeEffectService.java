package com.unique.examine.flow.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.ai.AiFieldFillFacade;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.core.api.WorkTaskCreationFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.interaction.FlowInteractionMutationService;
import com.unique.examine.flow.security.FlowSession;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Executes cross-domain node effects through existing owner facades. */
@Service
public class FlowNodeEffectService implements FlowNodeEffectPort {
    private final FlowInteractionMutationService interactions;
    private final ResultNotificationFacade notifications;
    private final WorkTaskCreationFacade workTasks;
    private final AiFieldFillFacade aiFills;
    private final OperationAuditFacade audits;
    private final ObjectMapper json;

    public FlowNodeEffectService(
            FlowInteractionMutationService interactions,
            ResultNotificationFacade notifications,
            WorkTaskCreationFacade workTasks,
            AiFieldFillFacade aiFills,
            OperationAuditFacade audits,
            ObjectMapper json) {
        this.interactions = Objects.requireNonNull(interactions, "interactions");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.workTasks = Objects.requireNonNull(workTasks, "workTasks");
        this.aiFills = Objects.requireNonNull(aiFills, "aiFills");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public ObjectNode execute(
            FlowSession session,
            ApprovalInstance instance,
            FlowExtensionGraph.Node node,
            JsonNode input,
            ObjectNode output,
            String idempotencyKey,
            String requestId,
            String traceId) {
        var result = output.deepCopy();
        switch (node.type()) {
            case COPY -> copy(session, instance, node, result);
            case NOTIFICATION, MESSAGE -> deliver(
                    session, instance, node, recipients(node, "recipients"), result);
            case TASK -> createTask(
                    session, instance, node, result, requestId, traceId);
            case AI_ASSIST -> prepareAi(
                    session, instance, node, input, result, requestId, traceId);
            default -> { }
        }
        audits.recordSuccess(OperationAudit.success(
                actor(session), context(session), aggregate(instance),
                action(node), null, result, request(requestId), request(traceId)));
        return result;
    }

    @Override
    public ObjectNode resume(
            FlowSession session,
            ApprovalInstance instance,
            FlowExtensionGraph.Node node,
            FlowExtensionRepository.NodeExecution current,
            JsonNode input,
            ObjectNode output,
            String idempotencyKey,
            String requestId,
            String traceId) {
        var result = output.deepCopy();
        if (node.type() == FlowNodeCatalog.Type.AI_ASSIST) {
            executeAi(session, instance, node, current,
                    result, requestId, traceId);
        } else if (node.type() == FlowNodeCatalog.Type.TASK) {
            requireTaskCompleted(
                    session, current, result, requestId, traceId);
        }
        audits.recordSuccess(OperationAudit.success(
                actor(session), context(session), aggregate(instance),
                action(node) + "_RESUME", current.result(), result,
                request(requestId), request(traceId)));
        return result;
    }

    @Override
    public void failed(
            FlowSession session,
            ApprovalInstance instance,
            FlowExtensionGraph.Node node,
            RuntimeException failure,
            String requestId,
            String traceId) {
        try {
            var code = failure instanceof BusinessException business
                    ? business.code() : failure.getClass().getSimpleName();
            audits.recordFailed(OperationAudit.failed(
                    actor(session), context(session), aggregate(instance),
                    action(node), null, null,
                    new OperationAudit.Failure(boundedCode(code)),
                    request(requestId), request(traceId)));
        } catch (RuntimeException auditFailure) {
            failure.addSuppressed(auditFailure);
        }
    }

    private void copy(
            FlowSession session,
            ApprovalInstance instance,
            FlowExtensionGraph.Node node,
            ObjectNode output) {
        var copies = output.putArray("copies");
        for (var recipient : recipients(node, "recipients")) {
            var value = interactions.copy(
                    session,
                    instance.id(),
                    new FlowRequests.Copy(
                            Long.toString(recipient),
                            node.config().path("message").asText("")),
                    effectKey(instance, node, "copy:" + recipient));
            copies.addObject()
                    .put("copyId", value.copyId())
                    .put("recipientId", value.recipientId())
                    .put("createdAt", value.createdAt());
        }
        output.put("copyCount", copies.size());
    }

    private void deliver(
            FlowSession session,
            ApprovalInstance instance,
            FlowExtensionGraph.Node node,
            long[] recipients,
            ObjectNode output) {
        var deliveries = output.putArray("deliveries");
        for (var recipient : recipients) {
            var receipt = notifications.dispatch(new ResultNotificationFacade.Command(
                    session.systemId(), session.tenantId(), session.memberId(), recipient,
                    node.config().path("templateCode").asText(), variables(node),
                    aggregate(instance), targetPath(session, instance),
                    effectKey(instance, node, "delivery:" + recipient)));
            receipt(deliveries, recipient, receipt);
        }
        output.put("deliveryCount", deliveries.size());
    }

    private void createTask(
            FlowSession session,
            ApprovalInstance instance,
            FlowExtensionGraph.Node node,
            ObjectNode output,
            String requestId,
            String traceId) {
        var config = node.config();
        var task = workTasks.create(new WorkTaskCreationFacade.Command(
                session.accountId(), session.systemId(), session.tenantId(),
                session.memberId(), session.permissions(),
                config.path("assigneeMemberId").asLong(),
                config.path("title").asText(), optionalText(config, "description"),
                config.hasNonNull("projectId") ? config.path("projectId").asLong() : null,
                config.hasNonNull("dueAt")
                        ? Instant.parse(config.path("dueAt").asText()) : null,
                aggregate(instance), effectKey(instance, node, "work-task"),
                request(requestId), request(traceId)));
        var taskJson = output.putObject("task")
                .put("taskId", task.taskId())
                .put("version", task.version())
                .put("systemId", task.systemId())
                .put("tenantId", task.tenantId())
                .put("creatorMemberId", task.creatorMemberId())
                .put("assigneeMemberId", task.assigneeMemberId())
                .put("title", task.title())
                .put("status", task.status())
                .put("createdAt", task.createdAt().toString())
                .put("replay", task.replay());
        if (task.description() != null) {
            taskJson.put("description", task.description());
        }
        if (task.projectId() != null) {
            taskJson.put("projectId", task.projectId());
        }
        if (task.dueAt() != null) {
            taskJson.put("dueAt", task.dueAt().toString());
        }
        output.put("taskId", task.taskId())
                .put("taskVersion", task.version())
                .put("taskStatus", task.status())
                .put("taskReplay", task.replay());
    }

    private void requireTaskCompleted(
            FlowSession session,
            FlowExtensionRepository.NodeExecution current,
            ObjectNode output,
            String requestId,
            String traceId) {
        var taskId = current.result().path("taskId").asLong(0);
        if (taskId <= 0) {
            throw new IllegalStateException("Flow task execution has no Work task identity");
        }
        var state = workTasks.state(new WorkTaskCreationFacade.StateQuery(
                session.accountId(), session.systemId(), session.tenantId(),
                session.memberId(), session.permissions(), taskId,
                request(requestId), request(traceId)));
        if (!"COMPLETED".equals(state.status())) {
            throw new BusinessException(
                    "FLOW_TASK_NOT_COMPLETED",
                    "The Work task must be completed before the Flow node resumes",
                    org.springframework.http.HttpStatus.CONFLICT);
        }
        output.put("taskId", state.taskId())
                .put("taskVersion", state.version())
                .put("taskStatus", state.status());
    }

    private void prepareAi(
            FlowSession session,
            ApprovalInstance instance,
            FlowExtensionGraph.Node node,
            JsonNode input,
            ObjectNode output,
            String requestId,
            String traceId) {
        var binding = requireBinding(instance);
        var fieldCode = node.config().path("fieldCode").asText();
        var source = aiFills.sourceSnapshot(new AiFieldFillFacade.SourceRequest(
                session.systemId(), session.tenantId(), session.memberId(),
                session.authorizationEpoch(), session.permissions(),
                binding.moduleCode(), Long.toString(binding.recordId()), fieldCode,
                request(requestId), request(traceId)));
        var supplied = object(input, "AI assist input");
        var result = supplied.path("result");
        if (result.isMissingNode() || result.isNull()) {
            throw new IllegalArgumentException("AI assist requires a result preview");
        }
        var provenance = provenance(supplied.path("provenance"));
        var proposalId = proposalId(instance, node);
        var prepared = aiFills.prepare(new AiFieldFillFacade.PrepareRequest(
                proposalId, session.systemId(), session.tenantId(), session.memberId(),
                session.authorizationEpoch(), session.permissions(), binding.moduleCode(),
                Long.toString(binding.recordId()), fieldCode, source.schemaVersionId(),
                source.recordVersion(), source.sourceVersionHash(), result.toString(),
                provenance, request(requestId), request(traceId)));
        output.put("proposalId", proposalId)
                .put("fieldCode", fieldCode)
                .put("recordVersion", source.recordVersion())
                .put("schemaVersionId", source.schemaVersionId())
                .set("preview", json.valueToTree(prepared.preview()));
        output.putObject("sealedCommand")
                .put("ciphertext", prepared.sealedCommand().ciphertext())
                .put("encryptionKeyVersion", prepared.sealedCommand().encryptionKeyVersion())
                .put("commandSha256", prepared.sealedCommand().commandSha256());
    }

    private void executeAi(
            FlowSession session,
            ApprovalInstance instance,
            FlowExtensionGraph.Node node,
            FlowExtensionRepository.NodeExecution current,
            ObjectNode output,
            String requestId,
            String traceId) {
        var binding = requireBinding(instance);
        var snapshot = current.result();
        var sealed = object(snapshot.path("sealedCommand"), "AI sealed command");
        var readback = aiFills.execute(new AiFieldFillFacade.ExecuteRequest(
                requiredText(snapshot, "proposalId"),
                session.systemId(), session.tenantId(), session.memberId(),
                session.authorizationEpoch(), session.permissions(),
                binding.moduleCode(), Long.toString(binding.recordId()),
                requiredText(snapshot, "fieldCode"),
                new AiFieldFillFacade.SealedCommand(
                        requiredText(sealed, "ciphertext"),
                        requiredText(sealed, "encryptionKeyVersion"),
                        requiredText(sealed, "commandSha256")),
                effectKey(instance, node, "ai-confirm"),
                request(requestId), request(traceId)));
        output.set("aiFill", json.valueToTree(readback));
        output.put("recordVersion", readback.recordVersion())
                .put("materializationVersion", readback.materializationVersion());
    }

    private static AiFieldFillFacade.Provenance provenance(JsonNode value) {
        var object = object(value, "AI provenance");
        return new AiFieldFillFacade.Provenance(
                requiredText(object, "providerId"),
                object.path("providerVersion").asLong(-1),
                requiredText(object, "model"),
                requiredText(object, "promptVersion"),
                requiredText(object, "policyVersionId"));
    }

    private static long[] recipients(
            FlowExtensionGraph.Node node, String field) {
        var values = node.config().path(field);
        var result = new long[values.size()];
        for (var index = 0; index < values.size(); index++) {
            result[index] = values.get(index).isTextual()
                    ? Long.parseLong(values.get(index).textValue())
                    : values.get(index).asLong();
        }
        return result;
    }

    private static Map<String, String> variables(FlowExtensionGraph.Node node) {
        var result = new LinkedHashMap<String, String>();
        var values = node.config().path("variables");
        if (values.isObject()) {
            values.fields().forEachRemaining(entry -> result.put(
                    entry.getKey(), entry.getValue().isTextual()
                            ? entry.getValue().textValue() : entry.getValue().toString()));
        }
        return Map.copyOf(result);
    }

    private static void receipt(
            ArrayNode output,
            long recipient,
            ResultNotificationFacade.DeliveryReceipt value) {
        var item = output.addObject()
                .put("recipientMemberId", recipient)
                .put("deliveryLogId", value.deliveryLogId())
                .put("status", value.status())
                .put("replay", value.replay());
        if (value.messageId() != null) item.put("messageId", value.messageId());
    }

    private static ApprovalInstance.RecordBinding requireBinding(
            ApprovalInstance instance) {
        if (instance.recordBinding() == null) {
            throw new IllegalArgumentException("AI assist requires a bound business record");
        }
        return instance.recordBinding();
    }

    private static ObjectNode object(JsonNode value, String name) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(name + " must be a JSON object");
        }
        return (ObjectNode) value;
    }

    private static String requiredText(JsonNode value, String field) {
        var item = value.path(field);
        if (!item.isTextual() || item.textValue().isBlank()) {
            throw new IllegalArgumentException("AI assist requires " + field);
        }
        return item.textValue();
    }

    private static String proposalId(
            ApprovalInstance instance, FlowExtensionGraph.Node node) {
        return "flow-" + instance.id() + "-v" + instance.definitionVersion()
                + "-" + node.code();
    }

    private static String effectKey(
            ApprovalInstance instance,
            FlowExtensionGraph.Node node,
            String suffix) {
        var material = instance.id() + ":v" + instance.definitionVersion()
                + ":" + node.code() + ":" + suffix;
        try {
            return "flow-node:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private static String targetPath(
            FlowSession session, ApprovalInstance instance) {
        return "/systems/" + session.systemId()
                + "/flows?instanceId=" + instance.id();
    }

    private static String optionalText(JsonNode value, String field) {
        var item = value.path(field);
        return item.isTextual() && !item.textValue().isBlank()
                ? item.textValue().strip() : null;
    }

    private static OperationAudit.Actor actor(FlowSession session) {
        return new OperationAudit.Actor(session.accountId(), "USER");
    }

    private static OperationAudit.Context context(FlowSession session) {
        return new OperationAudit.Context(
                ContextType.SYSTEM, session.systemId(), session.tenantId());
    }

    private static AggregateRef aggregate(ApprovalInstance instance) {
        return new AggregateRef("FLOW_INSTANCE", Long.toString(instance.id()));
    }

    private static String action(FlowExtensionGraph.Node node) {
        return "FLOW_NODE_" + node.type().name();
    }

    private static String request(String value) {
        var result = value == null || value.isBlank() ? "flow-node" : value;
        return result.length() <= 64 ? result : result.substring(0, 64);
    }

    private static String boundedCode(String value) {
        var normalized = value == null || value.isBlank()
                ? "FLOW_NODE_EFFECT_FAILED" : value;
        return normalized.length() <= 64
                ? normalized : normalized.substring(0, 64);
    }
}
