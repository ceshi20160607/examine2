package com.unique.examine.flow.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One sequential cursor or one adjacent all-success parallel completion join. */
public record ApprovalCompletionStage(
        int cursor,
        int endExclusive,
        String parallelGroup
) {
    public ApprovalCompletionStage {
        if (cursor < 0 || endExclusive <= cursor
                || endExclusive > ApprovalCompletionStep.MAX_STEPS
                || parallelGroup == null && endExclusive != cursor + 1
                || parallelGroup != null
                && !parallelGroup.matches("^[a-z][a-z0-9_]{0,63}$")) {
            throw conflict("Completion stage identity is invalid");
        }
    }

    public List<Integer> ordinals() {
        return java.util.stream.IntStream.range(cursor, endExclusive)
                .boxed().toList();
    }

    public static List<ApprovalCompletionStage> stages(
            List<ApprovalCompletionStep> steps
    ) {
        Objects.requireNonNull(steps, "steps");
        var snapshot = List.copyOf(steps);
        if (snapshot.size() > ApprovalCompletionStep.MAX_STEPS
                || snapshot.stream().anyMatch(Objects::isNull)) {
            throw conflict("Completion stage plan is invalid");
        }
        ApprovalCompletionStep.requireParallelGroups(snapshot);
        var result = new ArrayList<ApprovalCompletionStage>();
        for (var cursor = 0; cursor < snapshot.size();) {
            var group = snapshot.get(cursor).parallelGroup();
            var end = cursor + 1;
            if (group != null) {
                while (end < snapshot.size()
                        && group.equals(snapshot.get(end).parallelGroup())) {
                    end++;
                }
            }
            result.add(new ApprovalCompletionStage(cursor, end, group));
            cursor = end;
        }
        return List.copyOf(result);
    }

    public static ApprovalCompletionStage stageAtOrdinal(
            List<ApprovalCompletionExecution> executions,
            int ordinal
    ) {
        var snapshot = ordered(executions);
        if (ordinal < 0 || ordinal >= snapshot.size()) {
            throw conflict("Completion stage cursor is outside the plan");
        }
        return stages(snapshot.stream().map(
                        ApprovalCompletionExecution::step).toList()).stream()
                .filter(stage -> ordinal >= stage.cursor
                        && ordinal < stage.endExclusive)
                .findFirst().orElseThrow();
    }

    public static List<ApprovalCompletionExecution> activate(
            List<ApprovalCompletionExecution> executions,
            int cursor,
            Instant now
    ) {
        Objects.requireNonNull(now, "now");
        var snapshot = ordered(executions);
        var stage = stageAtOrdinal(snapshot, cursor);
        if (stage.cursor != cursor) {
            throw conflict("Completion activation cursor must be the stage start");
        }
        var initialMaterialization = cursor == 0
                && snapshot.getFirst().stateVersion() == 0
                && snapshot.getFirst().status()
                == ApprovalCompletionExecution.Status.AVAILABLE;
        var result = new ArrayList<ApprovalCompletionExecution>(snapshot);
        for (var ordinal = stage.cursor; ordinal < stage.endExclusive; ordinal++) {
            var execution = snapshot.get(ordinal);
            if (execution.status()
                    == ApprovalCompletionExecution.Status.AVAILABLE) {
                continue;
            }
            result.set(ordinal, initialMaterialization
                    ? execution.initiallyActivate(now)
                    : execution.activate(now));
        }
        return List.copyOf(result);
    }

    public static boolean joined(
            List<ApprovalCompletionExecution> executions,
            int cursor
    ) {
        var snapshot = ordered(executions);
        var stage = stageAtOrdinal(snapshot, cursor);
        if (stage.cursor != cursor) {
            throw conflict("Completion join cursor must be the stage start");
        }
        return snapshot.subList(stage.cursor, stage.endExclusive).stream()
                .allMatch(execution -> execution.status()
                        == ApprovalCompletionExecution.Status.SUCCEEDED);
    }

    public static List<ApprovalCompletionExecution> materializeInitialStage(
            List<ApprovalCompletionExecution> executions,
            Instant now
    ) {
        var snapshot = ordered(executions);
        if (snapshot.isEmpty()) {
            return snapshot;
        }
        return activate(snapshot, 0, now);
    }

    private static List<ApprovalCompletionExecution> ordered(
            List<ApprovalCompletionExecution> values
    ) {
        Objects.requireNonNull(values, "executions");
        var snapshot = List.copyOf(values);
        for (var index = 0; index < snapshot.size(); index++) {
            if (snapshot.get(index).ordinal() != index) {
                throw conflict("Completion execution plan must be ordinal ordered");
            }
        }
        ApprovalCompletionStep.requireParallelGroups(snapshot.stream()
                .map(ApprovalCompletionExecution::step).toList());
        return snapshot;
    }

    private static ApprovalDomainException conflict(String message) {
        return new ApprovalDomainException(
                ApprovalDomainException.Code.COMPLETION_STATE_CONFLICT,
                message);
    }
}
