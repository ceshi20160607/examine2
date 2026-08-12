package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.core.runtime.RuntimeRecordFlowTriggerFacade;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.domain.FlowTriggerDispatch;
import com.unique.examine.flow.domain.FlowTriggerDispatchInstance;
import com.unique.examine.flow.domain.TriggerBinding;
import com.unique.examine.flow.security.FlowSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class FlowRecordTriggerAdapter implements RuntimeRecordFlowTriggerFacade {
    private final FlowRequestServiceFactory services;
    private final FlowMutationService mutations;
    private final TriggerConditionMatcher conditions;
    private final ObjectMapper objectMapper;

    @Autowired
    public FlowRecordTriggerAdapter(
            FlowRequestServiceFactory services,
            FlowMutationService mutations,
            ObjectMapper objectMapper
    ) {
        this.services = Objects.requireNonNull(services, "services");
        this.mutations = Objects.requireNonNull(mutations, "mutations");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.conditions = new TriggerConditionMatcher(this.objectMapper);
    }

    public FlowRecordTriggerAdapter(
            FlowRequestServiceFactory services,
            FlowMutationService mutations
    ) {
        this(services, mutations, new ObjectMapper());
    }

    @Override
    @Transactional
    public TriggerResult trigger(TriggerRequest request) {
        Objects.requireNonNull(request, "request");
        var workflow = services.forTenant(request.systemId(), request.tenantId());
        var replay = workflow.triggerDispatchForUpdate(request.eventKey());
        if (replay.isPresent()) {
            return result(replay.get());
        }
        var event = TriggerBinding.Event.valueOf(request.event().name());
        var matches = workflow.triggerCandidates(request.moduleCode(), event).stream()
                .filter(version -> version.triggerBinding() != null)
                .filter(version -> conditions.matches(
                        version.triggerBinding(),
                        request.recordValuesJson()
                ))
                .toList();
        var exclusive = matches.stream()
                .filter(version -> version.triggerBinding().exclusive())
                .findFirst();
        var selected = exclusive.isPresent()
                ? List.of(exclusive.get())
                : matches.stream()
                        .filter(version -> !version.triggerBinding().exclusive())
                        .toList();
        if (selected.isEmpty()) {
            workflow.saveTriggerDispatch(FlowTriggerDispatch.noMatch(request.eventKey()));
            return new TriggerResult(List.of());
        }
        var session = new FlowSession(
                request.systemId(),
                request.tenantId(),
                request.actorMemberId(),
                request.effectivePermissions()
        );
        var startRequest = new FlowRequests.StartInstance(
                null,
                request.businessKey(),
                new FlowRequests.RecordBinding(
                        request.moduleCode(),
                        Long.toString(request.recordId())
                ),
                values(request.recordValuesJson())
        );
        var instances = new ArrayList<FlowTriggerDispatchInstance>();
        for (var ordinal = 0; ordinal < selected.size(); ordinal++) {
            var definition = selected.get(ordinal);
            var versionedRequest = new FlowRequests.StartInstance(
                    definition.version(),
                    startRequest.businessKey(),
                    startRequest.recordBinding(),
                    startRequest.values()
            );
            var started = ordinal == 0
                    ? mutations.startTriggered(
                            session,
                            definition.definitionId(),
                            versionedRequest)
                    : mutations.startAdditionalTriggered(
                            session,
                            definition.definitionId(),
                            versionedRequest,
                            request.eventKey()
                    );
            instances.add(new FlowTriggerDispatchInstance(
                    definition.definitionId(),
                    definition.version(),
                    Long.parseLong(started.instanceId())
            ));
        }
        var dispatch = FlowTriggerDispatch.results(request.eventKey(), instances);
        workflow.saveTriggerDispatch(dispatch);
        return result(dispatch);
    }

    private Map<String, JsonNode> values(Map<String, String> serialized) {
        var values = new java.util.LinkedHashMap<String, JsonNode>();
        serialized.forEach((field, value) -> {
            try {
                values.put(field, objectMapper.readTree(value));
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException(
                        "Record trigger value snapshot contains invalid JSON",
                        exception
                );
            }
        });
        return Map.copyOf(values);
    }

    private static TriggerResult result(FlowTriggerDispatch dispatch) {
        return new TriggerResult(dispatch.instances().stream()
                .map(instance -> new TriggeredInstance(
                        instance.definitionId(),
                        instance.definitionVersion(),
                        instance.instanceId()
                ))
                .toList());
    }
}
