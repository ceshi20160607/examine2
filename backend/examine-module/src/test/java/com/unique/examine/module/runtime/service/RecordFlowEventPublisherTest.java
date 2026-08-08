package com.unique.examine.module.runtime.service;

import com.unique.examine.core.runtime.RuntimeRecordFlowTriggerFacade;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordFlowEventPublisherTest {
    private static final Set<String> PERMISSIONS = Set.of(
            "system.runtime.access",
            "module.purchase_order.view",
            "module.purchase_order.update");

    @Test
    void capturesTheExactEventTriggerRequestThroughTheLazyProvider() {
        var captured = new AtomicReference<RuntimeRecordFlowTriggerFacade.TriggerRequest>();
        RuntimeRecordFlowTriggerFacade facade = request -> {
            captured.set(request);
            return new RuntimeRecordFlowTriggerFacade.TriggerResult(List.of(
                    new RuntimeRecordFlowTriggerFacade.TriggeredInstance(71L, 3, 81L)));
        };
        var beans = new StaticListableBeanFactory();
        beans.addBean("runtimeRecordFlowTriggerFacade", facade);
        var publisher = new RecordFlowEventPublisher(
                beans.getBeanProvider(RuntimeRecordFlowTriggerFacade.class));

        var result = publisher.publish(
                new RuntimeSession(9L, 11L, 33L, 22L, PERMISSIONS),
                "purchase_order",
                44L,
                6L,
                "R-44",
                RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_UPDATED,
                Map.of(
                        "amount", "100.00",
                        "urgent", "true"));

        assertThat(captured.get()).isEqualTo(new RuntimeRecordFlowTriggerFacade.TriggerRequest(
                11L,
                22L,
                33L,
                PERMISSIONS,
                "purchase_order",
                44L,
                6L,
                "R-44",
                RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_UPDATED,
                "record:11:22:purchase_order:44:6:RECORD_UPDATED",
                Map.of(
                        "amount", "100.00",
                        "urgent", "true")));
        assertThat(result.instances()).containsExactly(
                new RuntimeRecordFlowTriggerFacade.TriggeredInstance(71L, 3, 81L));
    }

    @Test
    void propagatesTriggerFailureToTheRecordMutationTransaction() {
        var failure = new IllegalStateException("trigger dispatch failed");
        RuntimeRecordFlowTriggerFacade facade = request -> {
            throw failure;
        };
        var beans = new StaticListableBeanFactory();
        beans.addBean("runtimeRecordFlowTriggerFacade", facade);
        var publisher = new RecordFlowEventPublisher(
                beans.getBeanProvider(RuntimeRecordFlowTriggerFacade.class));

        assertThatThrownBy(() -> publisher.publish(
                new RuntimeSession(9L, 11L, 33L, 22L, PERMISSIONS),
                "purchase_order",
                44L,
                6L,
                "R-44",
                RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_DELETED))
                .isSameAs(failure);
    }
}
