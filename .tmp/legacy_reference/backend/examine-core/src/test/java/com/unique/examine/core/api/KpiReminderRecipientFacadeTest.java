package com.unique.examine.core.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KpiReminderRecipientFacadeTest {
    @Test
    void exposesOnlyTheBoundedTenantScopedDepartmentLookup() throws Exception {
        var method = KpiReminderRecipientFacade.class.getMethod(
                "activeDepartmentMemberIds",
                long.class, long.class, long.class);

        assertThat(method.getReturnType()).isEqualTo(List.class);
        assertThat(KpiReminderRecipientFacade.MAX_RECIPIENTS)
                .isEqualTo(1_000);
        assertThat(KpiReminderRecipientFacade.class.getDeclaredMethods())
                .containsExactly(method);
    }
}
