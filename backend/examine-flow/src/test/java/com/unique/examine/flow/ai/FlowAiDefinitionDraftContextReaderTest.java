package com.unique.examine.flow.ai;

import com.unique.examine.core.ai.AiFlowDefinitionDraftFacade;
import com.unique.examine.core.api.EffectivePermissionFacade;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowAiDefinitionDraftContextReaderTest {
    @Test
    void requiresExactLiveManageAuthorization() {
        var live = new AtomicReference<>(evaluation(
                7, Set.of("flow.definition.manage")));
        var reader = reader(live, Set.of(10L, 20L, 30L));
        assertThat(reader.authorize(access()).memberId()).isEqualTo(10);

        live.set(evaluation(8, Set.of("flow.definition.manage")));
        assertCode(() -> reader.authorize(access()),
                "AI_FLOW_AUTHORIZATION_STALE");
        live.set(evaluation(8, Set.of()));
        assertCode(() -> reader.authorize(access()),
                "AI_FLOW_PERMISSION_DENIED");
    }

    @Test
    void prepareAndExecuteFactsRequireActiveOwnerAndEveryApprover() {
        var live = new AtomicReference<>(evaluation(
                7, Set.of("flow.definition.manage")));
        var active = ConcurrentHashMap.<Long>newKeySet();
        active.addAll(Set.of(10L, 20L, 30L));
        var reader = reader(live, active);
        var session = reader.validate(access(), draft());
        assertThat(session.accountId()).isEqualTo(99);

        active.remove(30L);
        assertCode(() -> reader.validateFacts(session, draft()),
                "AI_FLOW_MEMBER_UNAVAILABLE");
        active.add(30L);
        active.remove(10L);
        assertCode(() -> reader.validateFacts(session, draft()),
                "AI_FLOW_MEMBER_UNAVAILABLE");
    }

    private static FlowAiDefinitionDraftContextReader reader(
            AtomicReference<EffectivePermissionFacade.Evaluation> live,
            Set<Long> active) {
        RuntimeActiveMemberFacade members = (system, tenant, member) ->
                system == 1 && tenant == 2 && active.contains(member)
                        ? Optional.of(
                        new RuntimeActiveMemberFacade.ActiveMember(member, null))
                        : Optional.empty();
        return new FlowAiDefinitionDraftContextReader(
                (system, tenant, member) -> live.get(), members);
    }

    private static FlowAiDefinitionDraftContextReader.Access access() {
        return new FlowAiDefinitionDraftContextReader.Access(
                99, 1, 2, 10, 7,
                Set.of("flow.definition.manage"),
                AiFlowDefinitionDraftFacade.Operation.FLOW_DEFINITION_DRAFT);
    }

    private static AiFlowDefinitionDraftFacade.Draft draft() {
        return new AiFlowDefinitionDraftFacade.Draft(
                "Approval", List.of("20", "30"));
    }

    private static EffectivePermissionFacade.Evaluation evaluation(
            long epoch, Set<String> permissions) {
        return new EffectivePermissionFacade.Evaluation(
                epoch, false, permissions, List.of(), List.of());
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo(code));
    }
}
