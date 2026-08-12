package com.unique.examine.flow.domain;

import java.util.List;

public record FlowTriggerDispatch(
        String eventKey,
        Long definitionId,
        Integer definitionVersion,
        Long instanceId,
        List<FlowTriggerDispatchInstance> instances
) {
    public FlowTriggerDispatch {
        if (eventKey == null) {
            throw new IllegalArgumentException("Flow trigger event key is invalid");
        }
        eventKey = eventKey.strip();
        var length = eventKey.codePointCount(0, eventKey.length());
        if (length < 1 || length > 200) {
            throw new IllegalArgumentException("Flow trigger event key is invalid");
        }
        var noMatch = definitionId == null && definitionVersion == null && instanceId == null;
        var match = definitionId != null
                && definitionId > 0
                && definitionVersion != null
                && definitionVersion > 0
                && instanceId != null
                && instanceId > 0;
        if (!noMatch && !match) {
            throw new IllegalArgumentException("Flow trigger dispatch result is incomplete");
        }
        if (instances == null || instances.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Flow trigger dispatch instances are invalid");
        }
        instances = List.copyOf(instances);
        if (noMatch && !instances.isEmpty()) {
            throw new IllegalArgumentException("No-match dispatch cannot contain instances");
        }
        if (match && (instances.isEmpty()
                || instances.getFirst().definitionId() != definitionId
                || instances.getFirst().definitionVersion() != definitionVersion
                || instances.getFirst().instanceId() != instanceId)) {
            throw new IllegalArgumentException("Dispatch parent must snapshot the first instance");
        }
    }

    public FlowTriggerDispatch(
            String eventKey,
            Long definitionId,
            Integer definitionVersion,
            Long instanceId
    ) {
        this(
                eventKey,
                definitionId,
                definitionVersion,
                instanceId,
                instanceId == null
                        ? List.of()
                        : List.of(new FlowTriggerDispatchInstance(
                                definitionId,
                                definitionVersion,
                                instanceId
                        ))
        );
    }

    public static FlowTriggerDispatch noMatch(String eventKey) {
        return new FlowTriggerDispatch(eventKey, null, null, null);
    }

    public static FlowTriggerDispatch matched(
            String eventKey,
            long definitionId,
            int definitionVersion,
            long instanceId
    ) {
        return new FlowTriggerDispatch(
                eventKey,
                definitionId,
                definitionVersion,
                instanceId
        );
    }

    public static FlowTriggerDispatch results(
            String eventKey,
            List<FlowTriggerDispatchInstance> instances
    ) {
        if (instances == null || instances.isEmpty()) {
            return noMatch(eventKey);
        }
        var first = instances.getFirst();
        return new FlowTriggerDispatch(
                eventKey,
                first.definitionId(),
                first.definitionVersion(),
                first.instanceId(),
                instances
        );
    }

    public FlowTriggerDispatch withInstances(List<FlowTriggerDispatchInstance> values) {
        return new FlowTriggerDispatch(
                eventKey,
                definitionId,
                definitionVersion,
                instanceId,
                values
        );
    }

    public boolean matched() {
        return instanceId != null;
    }
}
