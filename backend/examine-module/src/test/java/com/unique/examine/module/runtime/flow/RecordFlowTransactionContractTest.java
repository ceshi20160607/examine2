package com.unique.examine.module.runtime.flow;

import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class RecordFlowTransactionContractTest {

    @Test
    void mutationsJoinTheCallerTransactionAndReadIsReadOnly() throws Exception {
        assertThat(RuntimeRecordFlowFacade.class)
                .isAssignableFrom(RuntimeRecordFlowAdapter.class);

        var bind = RuntimeRecordFlowAdapter.class.getMethod(
                "bind", RuntimeRecordFlowFacade.BindRequest.class);
        var transition = RuntimeRecordFlowAdapter.class.getMethod(
                "transition", RuntimeRecordFlowFacade.TransitionRequest.class);
        var bindAdditional = RuntimeRecordFlowAdapter.class.getMethod(
                "bindAdditional", RuntimeRecordFlowFacade.AdditionalBindRequest.class);
        var find = RecordFlowStateService.class.getMethod(
                "find",
                com.unique.examine.module.runtime.security.RuntimeSession.class,
                String.class,
                long.class);
        var findAll = RecordFlowStateService.class.getMethod(
                "findAll",
                com.unique.examine.module.runtime.security.RuntimeSession.class,
                String.class,
                long.class);

        assertThat(bind.getAnnotation(Transactional.class)).isNotNull();
        assertThat(bindAdditional.getAnnotation(Transactional.class)).isNotNull();
        assertThat(transition.getAnnotation(Transactional.class)).isNotNull();
        assertThat(find.getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(findAll.getAnnotation(Transactional.class).readOnly()).isTrue();
    }
}
