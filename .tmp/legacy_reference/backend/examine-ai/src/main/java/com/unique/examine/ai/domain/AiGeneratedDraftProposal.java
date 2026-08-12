package com.unique.examine.ai.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Confirmation-gated proposal for one generated Flow, report, or print draft. */
public record AiGeneratedDraftProposal(
        long id,
        long accountId,
        long systemId,
        long tenantId,
        long memberId,
        long sessionId,
        long turnId,
        long policyVersionId,
        long providerId,
        long providerVersion,
        long authorizationEpoch,
        String promptVersion,
        Operation operation,
        String planHash,
        State state,
        long revision,
        Preview preview,
        double confidence,
        String clarification,
        SealedCommand sealedCommand,
        Instant expiresAt,
        Long actedBy,
        Result result,
        String resultCode,
        String requestId,
        String traceId,
        Instant createdAt,
        Instant updatedAt,
        Instant finishedAt
) {
    public static final String REDACTED_NAME = "[name:redacted]";
    public static final String REDACTED_DESCRIPTION = "[description:redacted]";
    public static final String REDACTED_TITLE = "[title:redacted]";
    public static final String REDACTED_FOOTER = "[footer:redacted]";
    public static final String REDACTED_CLARIFICATION = "[clarification:redacted]";

    public AiGeneratedDraftProposal {
        positive(id, "id"); positive(accountId, "accountId");
        positive(systemId, "systemId"); positive(tenantId, "tenantId");
        positive(memberId, "memberId"); positive(sessionId, "sessionId");
        positive(turnId, "turnId"); positive(policyVersionId, "policyVersionId");
        positive(providerId, "providerId");
        if (providerVersion < 0 || authorizationEpoch <= 0 || revision < 0) {
            invalid("snapshot");
        }
        promptVersion = token(promptVersion, "promptVersion", 64);
        operation = Objects.requireNonNull(operation, "operation");
        hash(planHash, "planHash");
        state = Objects.requireNonNull(state, "state");
        if (!Double.isFinite(confidence) || confidence < 0d || confidence > 1d) {
            invalid("confidence");
        }
        if (clarification != null) {
            clarification = text(clarification, "clarification", 500);
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!expiresAt.isAfter(createdAt) || updatedAt.isBefore(createdAt)) {
            invalid("timestamps");
        }
        if (actedBy != null && actedBy <= 0) invalid("actedBy");
        resultCode = token(resultCode, "resultCode", 64);
        requestId = token(requestId, "requestId", 64);
        traceId = token(traceId, "traceId", 64);

        if (state == State.CLARIFICATION_REQUIRED) {
            if (preview != null || sealedCommand != null || clarification == null
                    || actedBy != null || result != null || finishedAt != null) {
                invalid("clarification state");
            }
        } else {
            preview = Objects.requireNonNull(preview, "preview");
            if (preview.operation() != operation) invalid("preview operation");
            sealedCommand = Objects.requireNonNull(sealedCommand, "sealedCommand");
            if (clarification != null) invalid("actionable clarification");
        }
        if (state == State.PENDING) {
            if (actedBy != null || result != null || finishedAt != null) {
                invalid("pending state");
            }
        } else if (state == State.EXECUTING) {
            positive(Objects.requireNonNull(actedBy, "actedBy"), "actedBy");
            if (result != null || finishedAt != null) invalid("executing state");
        } else if (state != State.CLARIFICATION_REQUIRED && finishedAt == null) {
            invalid("terminal state");
        }
        if (state == State.SUCCEEDED
                && (result == null || result.operation() != operation)) {
            invalid("success result");
        }
        if (state != State.SUCCEEDED && result != null) {
            invalid("non-success result");
        }
    }

    public enum Operation {
        FLOW_DEFINITION_DRAFT,
        CONFIG_REPORT_DRAFT,
        CONFIG_PRINT_TEMPLATE_DRAFT
    }

    public enum State {
        CLARIFICATION_REQUIRED,
        PENDING,
        EXECUTING,
        SUCCEEDED,
        FAILED,
        REJECTED,
        EXPIRED,
        STALE,
        PERMISSION_DENIED
    }

    public record FlowPreview(String name, List<String> approverMemberIds) {
        public FlowPreview {
            name = text(name, "flow name", 200);
            approverMemberIds = positiveDecimals(
                    approverMemberIds, "approverMemberIds", 10);
        }
    }

    public record ReportPreview(
            String code,
            String name,
            String description,
            String dataSourceId,
            List<String> outputFieldCodes
    ) {
        public ReportPreview {
            code = AiGeneratedDraftProposal.code(code, "report code");
            name = text(name, "report name", 200);
            if (description != null) {
                description = text(description, "report description", 2_000);
            }
            dataSourceId = positiveDecimal(dataSourceId, "dataSourceId");
            outputFieldCodes = codes(outputFieldCodes, "outputFieldCodes", 100);
        }
    }

    public record PrintPreview(
            String moduleCode,
            String code,
            String name,
            String paperSize,
            String orientation,
            String title,
            List<String> fieldCodes,
            String footer
    ) {
        public PrintPreview {
            moduleCode = lowerCode(moduleCode, "moduleCode");
            code = lowerCode(code, "print code");
            name = text(name, "print name", 128);
            paperSize = oneOf(paperSize, "paperSize", "A4", "A5");
            orientation = oneOf(
                    orientation, "orientation", "PORTRAIT", "LANDSCAPE");
            title = text(title, "print title", 160);
            fieldCodes = codes(fieldCodes, "fieldCodes", 50);
            if (footer != null) footer = text(footer, "print footer", 300);
        }
    }

    public record Preview(
            Operation operation,
            FlowPreview flowDefinition,
            ReportPreview report,
            PrintPreview printTemplate
    ) {
        public Preview {
            operation = Objects.requireNonNull(operation, "operation");
            matching(operation, flowDefinition, report, printTemplate);
        }
    }

    public record SealedCommand(
            String ciphertext,
            String keyVersion,
            String commandHash
    ) {
        public SealedCommand {
            ciphertext = text(ciphertext, "ciphertext", 262_144);
            keyVersion = token(keyVersion, "keyVersion", 64);
            hash(commandHash, "commandHash");
        }
    }

    public record FlowResult(
            String definitionId,
            String name,
            List<String> approverMemberIds,
            long revision,
            Instant updatedAt,
            boolean published
    ) {
        public FlowResult {
            definitionId = positiveDecimal(definitionId, "definitionId");
            name = text(name, "flow result name", 200);
            approverMemberIds = positiveDecimals(
                    approverMemberIds, "result approverMemberIds", 10);
            nonNegative(revision, "flow revision");
            Objects.requireNonNull(updatedAt, "updatedAt");
            if (published) invalid("flow published");
        }
    }

    public record ReportResult(
            String reportId,
            String code,
            String name,
            String description,
            String dataSourceId,
            List<String> outputFieldCodes,
            long draftVersion,
            long version,
            Instant createdAt,
            Instant updatedAt,
            boolean published
    ) {
        public ReportResult {
            reportId = positiveDecimal(reportId, "reportId");
            code = AiGeneratedDraftProposal.code(code, "report result code");
            name = text(name, "report result name", 200);
            if (description != null) {
                description = text(description, "report result description", 2_000);
            }
            dataSourceId = positiveDecimal(dataSourceId, "result dataSourceId");
            outputFieldCodes = codes(
                    outputFieldCodes, "result outputFieldCodes", 100);
            positive(draftVersion, "report draftVersion");
            positive(version, "report version");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            if (updatedAt.isBefore(createdAt) || published) invalid("report published");
        }
    }

    public record PrintResult(
            String templateId,
            String moduleId,
            String moduleCode,
            String code,
            String name,
            String paperSize,
            String orientation,
            String title,
            List<String> fieldCodes,
            String footer,
            String status,
            long version,
            Instant updatedAt,
            boolean published
    ) {
        public PrintResult {
            templateId = positiveDecimal(templateId, "templateId");
            moduleId = positiveDecimal(moduleId, "moduleId");
            moduleCode = lowerCode(moduleCode, "result moduleCode");
            code = lowerCode(code, "print result code");
            name = text(name, "print result name", 128);
            paperSize = oneOf(paperSize, "paperSize", "A4", "A5");
            orientation = oneOf(
                    orientation, "orientation", "PORTRAIT", "LANDSCAPE");
            title = text(title, "print result title", 160);
            fieldCodes = codes(fieldCodes, "result fieldCodes", 50);
            if (footer != null) footer = text(footer, "print result footer", 300);
            if (!"DISABLED".equals(status)) invalid("print result status");
            nonNegative(version, "print version");
            Objects.requireNonNull(updatedAt, "updatedAt");
            if (published) invalid("print published");
        }
    }

    public record Result(
            Operation operation,
            FlowResult flowDefinition,
            ReportResult report,
            PrintResult printTemplate
    ) {
        public Result {
            operation = Objects.requireNonNull(operation, "operation");
            matching(operation, flowDefinition, report, printTemplate);
        }
    }

    public record Attempt(
            long id,
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long sessionId,
            long turnId,
            long policyVersionId,
            long providerId,
            long providerVersion,
            long proposalId,
            String action,
            String requestKey,
            String requestHash,
            State status,
            String resultHash,
            String resultCode,
            Instant createdAt,
            Instant finishedAt
    ) {
        public Attempt {
            positive(id, "attempt id"); positive(accountId, "attempt accountId");
            positive(systemId, "attempt systemId");
            positive(tenantId, "attempt tenantId");
            positive(memberId, "attempt memberId");
            positive(sessionId, "attempt sessionId");
            positive(turnId, "attempt turnId");
            positive(policyVersionId, "attempt policy");
            positive(providerId, "attempt provider");
            positive(proposalId, "attempt proposal");
            if (providerVersion < 0) invalid("attempt providerVersion");
            action = token(action, "action", 16);
            requestKey = token(requestKey, "requestKey", 128);
            hash(requestHash, "requestHash");
            status = Objects.requireNonNull(status, "status");
            if (resultHash != null) hash(resultHash, "resultHash");
            resultCode = token(resultCode, "resultCode", 64);
            Objects.requireNonNull(createdAt, "createdAt");
            if ((status == State.EXECUTING) == (finishedAt != null)) {
                invalid("attempt finishedAt");
            }
        }
    }

    public record Event(
            long id,
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long sessionId,
            long turnId,
            long policyVersionId,
            long providerId,
            long providerVersion,
            long proposalId,
            Long attemptId,
            String eventType,
            State fromState,
            State toState,
            long revision,
            long actorMemberId,
            String requestId,
            String traceId,
            String resultCode,
            String eventHash,
            Instant createdAt
    ) {
        public Event {
            positive(id, "event id"); positive(accountId, "event accountId");
            positive(systemId, "event systemId");
            positive(tenantId, "event tenantId");
            positive(memberId, "event memberId");
            positive(sessionId, "event sessionId");
            positive(turnId, "event turnId");
            positive(policyVersionId, "event policy");
            positive(providerId, "event provider");
            positive(proposalId, "event proposal");
            if (providerVersion < 0 || revision < 0) invalid("event version");
            if (attemptId != null && attemptId <= 0) invalid("attemptId");
            eventType = token(eventType, "eventType", 32);
            toState = Objects.requireNonNull(toState, "toState");
            positive(actorMemberId, "actorMemberId");
            requestId = token(requestId, "requestId", 64);
            traceId = token(traceId, "traceId", 64);
            resultCode = token(resultCode, "resultCode", 64);
            hash(eventHash, "eventHash");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    private static void matching(
            Operation operation, Object flow, Object report, Object print) {
        if ((operation == Operation.FLOW_DEFINITION_DRAFT) != (flow != null)
                || (operation == Operation.CONFIG_REPORT_DRAFT) != (report != null)
                || (operation == Operation.CONFIG_PRINT_TEMPLATE_DRAFT) != (print != null)) {
            invalid("payload");
        }
    }

    private static List<String> positiveDecimals(
            List<String> values, String field, int maximum) {
        Objects.requireNonNull(values, field);
        if (values.isEmpty() || values.size() > maximum
                || values.stream().distinct().count() != values.size()) invalid(field);
        return values.stream().map(value -> positiveDecimal(value, field)).toList();
    }

    private static List<String> codes(
            List<String> values, String field, int maximum) {
        Objects.requireNonNull(values, field);
        if (values.isEmpty() || values.size() > maximum
                || values.stream().distinct().count() != values.size()) invalid(field);
        return values.stream().map(value -> code(value, field)).toList();
    }

    private static String positiveDecimal(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(
                    "AI generated-draft proposal " + field + " is invalid", failure);
        }
    }

    private static String code(String value, String field) {
        value = text(value, field, 64);
        if (!value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) invalid(field);
        return value;
    }

    private static String lowerCode(String value, String field) {
        value = text(value, field, 64);
        if (!value.matches("^[a-z][a-z0-9_]{1,63}$")) invalid(field);
        return value;
    }

    private static String oneOf(
            String value, String field, String first, String second) {
        value = text(value, field, 16);
        if (!value.equals(first) && !value.equals(second)) invalid(field);
        return value;
    }

    private static String token(String value, String field, int maximum) {
        value = text(value, field, maximum);
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,"
                + (maximum - 1) + "}$")) invalid(field);
        return value;
    }

    private static String text(String value, String field, int maximum) {
        if (value == null || value.isBlank()) invalid(field);
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) invalid(field);
        return value;
    }

    private static void hash(String value, String field) {
        if (value == null || !value.matches("^[0-9a-f]{64}$")) invalid(field);
    }

    private static void positive(long value, String field) {
        if (value <= 0) invalid(field);
    }

    private static void nonNegative(long value, String field) {
        if (value < 0) invalid(field);
    }

    private static void invalid(String field) {
        throw new IllegalArgumentException(
                "AI generated-draft proposal " + field + " is invalid");
    }
}
