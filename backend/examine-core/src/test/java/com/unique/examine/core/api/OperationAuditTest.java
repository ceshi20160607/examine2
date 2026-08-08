package com.unique.examine.core.api;

import com.unique.examine.core.context.ContextType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationAuditTest {
    @Test
    void enforcesResultAndContextShapes() {
        var actor = new OperationAudit.Actor(1L, "WEB");
        var platform = new OperationAudit.Context(ContextType.PLATFORM, null, null);
        var aggregate = new AggregateRef("ROLE", "2");

        var success = OperationAudit.success(
                actor, platform, aggregate, "ROLE_PUBLISHED", null, java.util.Map.of("version", 2),
                "request-id", "trace-id"
        );
        assertThat(success.result()).isEqualTo(OperationAudit.Result.SUCCESS);

        assertThatThrownBy(() -> new OperationAudit(
                actor, platform, aggregate, "ROLE_PUBLISHED", null, null,
                OperationAudit.Result.DENIED, null, "request-id", "trace-id"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires failure");

        assertThatThrownBy(() -> new OperationAudit.Context(ContextType.PLATFORM, 3L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform context");
    }
}
