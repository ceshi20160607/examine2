package com.unique.examine.flow.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Exact-version subflow validation. No dynamic lookup or graph jump is allowed. */
public final class ApprovalSubflowGraph {
    public static final int MAX_DIAGNOSTIC_PATH = 10;

    private ApprovalSubflowGraph() {
    }

    public static void validate(
            long definitionId,
            int version,
            long systemId,
            long tenantId,
            TargetResolver resolver
    ) {
        if (definitionId <= 0 || version < 1 || systemId <= 0 || tenantId <= 0) {
            throw invalid("Subflow graph root is invalid");
        }
        Objects.requireNonNull(resolver, "resolver");
        walk(new TargetKey(definitionId, version), systemId, tenantId,
                resolver, new ArrayList<>(), new HashSet<>());
    }

    /** Validates a not-yet-persisted parent version against exact published
     * child versions supplied by the resolver. */
    public static void validateCompletionSteps(
            long definitionId,
            int version,
            long systemId,
            long tenantId,
            List<ApprovalCompletionStep> completionSteps,
            TargetResolver resolver
    ) {
        if (definitionId <= 0 || version < 1 || systemId <= 0 || tenantId <= 0) {
            throw invalid("Subflow graph root is invalid");
        }
        Objects.requireNonNull(resolver, "resolver");
        var path = new ArrayList<TargetKey>();
        path.add(new TargetKey(definitionId, version));
        var complete = new HashSet<TargetKey>();
        for (var step : ApprovalCompletionStep.requireSteps(completionSteps)) {
            if (step.type() == ApprovalCompletionStep.Type.SUBFLOW) {
                if (step.subflow().definitionId() == definitionId) {
                    throw new ApprovalDomainException(
                            ApprovalDomainException.Code.SUBFLOW_CYCLE,
                            "Subflow cycle detected: " + definitionId + "@"
                                    + version + " -> " + definitionId + "@"
                                    + step.subflow().version());
                }
                walk(new TargetKey(
                                step.subflow().definitionId(),
                                step.subflow().version()),
                        systemId, tenantId, resolver, path, complete);
            }
        }
    }

    private static void walk(
            TargetKey key,
            long systemId,
            long tenantId,
            TargetResolver resolver,
            List<TargetKey> path,
            Set<TargetKey> complete
    ) {
        if (path.size() > ApprovalStartContext.MAX_SUBFLOW_DEPTH) {
            var diagnostic = new ArrayList<>(path);
            diagnostic.add(key);
            var bounded = diagnostic.stream()
                    .limit(MAX_DIAGNOSTIC_PATH)
                    .map(TargetKey::toString)
                    .reduce((left, right) -> left + " -> " + right)
                    .orElse(key.toString());
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.SUBFLOW_DEPTH_EXCEEDED,
                    "Subflow depth exceeds eight: " + bounded);
        }
        var repeatedAt = -1;
        for (var index = 0; index < path.size(); index++) {
            if (path.get(index).definitionId() == key.definitionId()) {
                repeatedAt = index;
                break;
            }
        }
        if (repeatedAt >= 0) {
            var cycle = new ArrayList<>(path.subList(repeatedAt, path.size()));
            cycle.add(key);
            var diagnostic = cycle.stream()
                    .limit(MAX_DIAGNOSTIC_PATH)
                    .map(TargetKey::toString)
                    .reduce((left, right) -> left + " -> " + right)
                    .orElse(key.toString());
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.SUBFLOW_CYCLE,
                    "Subflow cycle detected: " + diagnostic);
        }
        if (complete.contains(key)) {
            return;
        }
        var target = resolver.resolve(key.definitionId(), key.version());
        if (target == null || !target.published() || !target.enabled()
                || target.systemId() != systemId || target.tenantId() != tenantId
                || target.definitionId() != key.definitionId()
                || target.version() != key.version()) {
            throw invalid("Subflow target must be the exact enabled published version in the same scope");
        }
        path.add(key);
        for (var step : target.completionSteps()) {
            if (step.type() == ApprovalCompletionStep.Type.SUBFLOW) {
                walk(new TargetKey(
                                step.subflow().definitionId(),
                                step.subflow().version()),
                        systemId, tenantId, resolver, path, complete);
            }
        }
        path.removeLast();
        complete.add(key);
    }

    @FunctionalInterface
    public interface TargetResolver {
        Target resolve(long definitionId, int version);
    }

    public record Target(
            long systemId,
            long tenantId,
            long definitionId,
            int version,
            boolean published,
            boolean enabled,
            List<ApprovalCompletionStep> completionSteps
    ) {
        public Target {
            completionSteps = ApprovalCompletionStep.requireSteps(completionSteps);
        }
    }

    private record TargetKey(long definitionId, int version) {
        @Override
        public String toString() {
            return definitionId + "@" + version;
        }
    }

    private static ApprovalDomainException invalid(String message) {
        return new ApprovalDomainException(
                ApprovalDomainException.Code.SUBFLOW_TARGET_INVALID, message);
    }
}
