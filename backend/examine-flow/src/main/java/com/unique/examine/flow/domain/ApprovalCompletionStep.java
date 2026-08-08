package com.unique.examine.flow.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.COMPLETION_STEP_INVALID;

/**
 * Immutable definition-time completion step. Exactly one type-specific
 * configuration is present.
 */
public record ApprovalCompletionStep(
        String code,
        String name,
        Type type,
        ExternalTask externalTask,
        Webhook webhook,
        Subflow subflow,
        String parallelGroup,
        Compensation compensation
) {
    public static final int MAX_STEPS = 8;
    private static final String CODE_PATTERN = "^[a-z][a-z0-9_]{0,63}$";

    public ApprovalCompletionStep {
        if (code == null || !code.matches(CODE_PATTERN)) {
            throw invalid("Completion step code is invalid");
        }
        name = bounded(name, "Completion step name", 80);
        Objects.requireNonNull(type, "type");
        var configured = (externalTask == null ? 0 : 1)
                + (webhook == null ? 0 : 1)
                + (subflow == null ? 0 : 1);
        if (configured != 1
                || type == Type.EXTERNAL_TASK && externalTask == null
                || type == Type.WEBHOOK && webhook == null
                || type == Type.SUBFLOW && subflow == null) {
            throw invalid(
                    "Completion step requires exactly its matching configuration");
        }
        if (parallelGroup != null) {
            parallelGroup = parallelGroup.strip();
            if (!parallelGroup.matches(CODE_PATTERN)) {
                throw invalid("Completion parallel group is invalid");
            }
        }
    }

    public ApprovalCompletionStep(
            String code, String name, Type type, ExternalTask externalTask,
            Webhook webhook, Subflow subflow, String parallelGroup
    ) {
        this(code, name, type, externalTask, webhook, subflow,
                parallelGroup, null);
    }

    public ApprovalCompletionStep(
            String code, String name, Type type, ExternalTask externalTask,
            Webhook webhook, Subflow subflow
    ) {
        this(code, name, type, externalTask, webhook, subflow, null, null);
    }

    public ApprovalCompletionStep(
            String code,
            String name,
            Type type,
            ExternalTask externalTask,
            Webhook webhook
    ) {
        this(code, name, type, externalTask, webhook, null, null, null);
    }

    public static ApprovalCompletionStep externalTask(
            String code,
            String name,
            ExternalTask config
    ) {
        return new ApprovalCompletionStep(
                code, name, Type.EXTERNAL_TASK, config, null, null, null, null);
    }

    public static ApprovalCompletionStep webhook(
            String code,
            String name,
            Webhook config
    ) {
        return new ApprovalCompletionStep(
                code, name, Type.WEBHOOK, null, config, null, null, null);
    }

    public static ApprovalCompletionStep subflow(
            String code,
            String name,
            Subflow config
    ) {
        return new ApprovalCompletionStep(
                code, name, Type.SUBFLOW, null, null, config, null, null);
    }

    public ApprovalCompletionStep withParallelGroup(String group) {
        return new ApprovalCompletionStep(
                code, name, type, externalTask, webhook, subflow, group,
                compensation);
    }

    public ApprovalCompletionStep withCompensation(Compensation value) {
        return new ApprovalCompletionStep(
                code, name, type, externalTask, webhook, subflow,
                parallelGroup, value);
    }

    public int maxAttempts() {
        return switch (type) {
            case EXTERNAL_TASK -> externalTask.maxAttempts();
            case WEBHOOK -> webhook.maxAttempts();
            case SUBFLOW -> Integer.MAX_VALUE;
        };
    }

    public static List<ApprovalCompletionStep> requireSteps(
            List<ApprovalCompletionStep> values
    ) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        var steps = List.copyOf(values);
        if (steps.size() > MAX_STEPS
                || steps.stream().anyMatch(Objects::isNull)
                || steps.stream().map(ApprovalCompletionStep::code)
                .distinct().count() != steps.size()) {
            throw invalid(
                    "Completion steps must contain 1..8 unique ordered codes");
        }
        requireParallelGroups(steps);
        return steps;
    }

    static void requireParallelGroups(List<ApprovalCompletionStep> steps) {
        var closedGroups = new java.util.HashSet<String>();
        for (var index = 0; index < steps.size();) {
            var group = steps.get(index).parallelGroup();
            if (group == null) {
                index++;
                continue;
            }
            if (closedGroups.contains(group)) {
                throw invalid("Completion parallel group reuse must be adjacent");
            }
            var end = index + 1;
            while (end < steps.size()
                    && group.equals(steps.get(end).parallelGroup())) {
                end++;
            }
            if (end - index < 2) {
                throw invalid("Completion parallel group requires 2..8 adjacent steps");
            }
            closedGroups.add(group);
            index = end;
        }
    }

    public enum Type {
        EXTERNAL_TASK,
        WEBHOOK,
        SUBFLOW
    }

    public record ExternalTask(
            String topic,
            int leaseSeconds,
            int maxAttempts,
            int resultJsonLimitBytes
    ) {
        private static final String TOPIC_PATTERN =
                "^[a-z][a-z0-9._-]{0,63}$";

        public ExternalTask {
            if (topic == null || !topic.matches(TOPIC_PATTERN)
                    || leaseSeconds < 30 || leaseSeconds > 900
                    || maxAttempts < 1 || maxAttempts > 10
                    || resultJsonLimitBytes < 1
                    || resultJsonLimitBytes > 8192) {
                throw invalid(
                        "External-task completion configuration is invalid");
            }
        }
    }

    public record Webhook(
            String url,
            String secretRef,
            int timeoutSeconds,
            int maxAttempts,
            int baseBackoffSeconds
    ) {
        public Webhook {
            url = bounded(url, "Webhook URL", 1024);
            requireSafeHttpsUri(url);
            if (secretRef != null) {
                secretRef = bounded(
                        secretRef, "Webhook secret reference", 512);
            }
            if (timeoutSeconds < 1 || timeoutSeconds > 30
                    || maxAttempts < 1 || maxAttempts > 10
                    || baseBackoffSeconds < 1
                    || baseBackoffSeconds > 300) {
                throw invalid("Webhook completion configuration is invalid");
            }
        }

        private static void requireSafeHttpsUri(String value) {
            try {
                var uri = new URI(value);
                if (!"https".equalsIgnoreCase(uri.getScheme())
                        || uri.getHost() == null
                        || uri.getHost().isBlank()
                        || uri.getUserInfo() != null
                        || uri.getFragment() != null) {
                    throw invalid(
                            "Webhook URL must be an HTTPS origin without credentials or fragment");
                }
            } catch (URISyntaxException exception) {
                throw invalid("Webhook URL is invalid");
            }
        }
    }

    /** Exact published child version frozen with its parent version. */
    public record Subflow(long definitionId, int version) {
        public Subflow {
            if (definitionId <= 0 || version < 1) {
                throw invalid(
                        "Subflow completion target requires an exact definition and version");
            }
        }
    }

    /** One immutable reverse action using an existing completion protocol. */
    public record Compensation(
            Type type,
            ExternalTask externalTask,
            Webhook webhook,
            Subflow subflow
    ) {
        public Compensation {
            Objects.requireNonNull(type, "type");
            var configured = (externalTask == null ? 0 : 1)
                    + (webhook == null ? 0 : 1)
                    + (subflow == null ? 0 : 1);
            if (configured != 1
                    || type == Type.EXTERNAL_TASK && externalTask == null
                    || type == Type.WEBHOOK && webhook == null
                    || type == Type.SUBFLOW && subflow == null) {
                throw invalid("Compensation requires exactly its matching configuration");
            }
        }

        public static Compensation externalTask(ExternalTask value) {
            return new Compensation(Type.EXTERNAL_TASK, value, null, null);
        }

        public static Compensation webhook(Webhook value) {
            return new Compensation(Type.WEBHOOK, null, value, null);
        }

        public static Compensation subflow(Subflow value) {
            return new Compensation(Type.SUBFLOW, null, null, value);
        }

        public ApprovalCompletionStep asStep(ApprovalCompletionStep original) {
            return new ApprovalCompletionStep(
                    original.code(), original.name(), type,
                    externalTask, webhook, subflow, null, null);
        }
    }

    static String bounded(String value, String label, int maximum) {
        if (value == null || value.isBlank()) {
            throw invalid(label + " is required");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw invalid(label + " accepts at most " + maximum + " characters");
        }
        return value;
    }

    private static ApprovalDomainException invalid(String message) {
        return new ApprovalDomainException(COMPLETION_STEP_INVALID, message);
    }
}
