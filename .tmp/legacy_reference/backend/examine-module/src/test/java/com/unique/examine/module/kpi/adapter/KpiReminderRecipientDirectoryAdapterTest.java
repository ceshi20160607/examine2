package com.unique.examine.module.kpi.adapter;

import com.unique.examine.core.api.KpiReminderRecipientFacade;
import com.unique.examine.module.kpi.port.KpiReminderRecipientDirectory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class KpiReminderRecipientDirectoryAdapterTest {
    @Test
    void mapsDepartmentRecipientsAsABoundedCanonicalSnapshot() {
        var facade = new FakeFacade();
        var adapter = new KpiReminderRecipientDirectoryAdapter(facade);

        var result = adapter.activeDepartmentMemberIds(10, 20, 30);

        assertThat(result)
                .hasSize(KpiReminderRecipientDirectory.MAX_RECIPIENTS);
        assertThat(result.getFirst()).isEqualTo(1L);
        assertThat(result.getLast()).isEqualTo(1_000L);
        assertThat(facade.scope).containsExactly(10L, 20L, 30L);
    }

    @Test
    void preservesTheEmptyHiddenResult() {
        var facade = new FakeFacade();
        facade.hidden = true;

        assertThat(new KpiReminderRecipientDirectoryAdapter(facade)
                .activeDepartmentMemberIds(10, 20, 31)).isEmpty();
    }

    private static final class FakeFacade
            implements KpiReminderRecipientFacade {
        private boolean hidden;
        private List<Long> scope = List.of();

        @Override
        public List<Long> activeDepartmentMemberIds(
                long systemId,
                long tenantId,
                long departmentId
        ) {
            scope = List.of(systemId, tenantId, departmentId);
            if (hidden) {
                return List.of();
            }
            var values = new ArrayList<Long>(LongStream
                    .rangeClosed(1, 1_005)
                    .map(value -> 1_006 - value)
                    .boxed()
                    .toList());
            values.add(5L);
            values.add(-1L);
            values.add(null);
            return values;
        }
    }
}
