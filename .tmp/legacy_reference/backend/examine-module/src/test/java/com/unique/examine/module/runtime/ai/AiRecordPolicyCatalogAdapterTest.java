package com.unique.examine.module.runtime.ai;

import com.unique.examine.core.ai.AiRecordPolicyCatalogFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRecordPolicyCatalogAdapterTest {
    private static final Set<String> VIEW = Set.of(
            "system.runtime.access",
            "module.work_order.view",
            "module.work_order.field.name.read");

    @Test
    void projectsOnlyReadableStableFieldCodesFromTheRuntimeSchema() throws Exception {
        var seenSession = new AtomicReference<RuntimeSession>();
        var seenModule = new AtomicReference<String>();
        var adapter = new AiRecordPolicyCatalogAdapter((session, moduleCode) -> {
            seenSession.set(session);
            seenModule.set(moduleCode);
            return schema(List.of(
                    field("name", true),
                    field("secret_note", false),
                    field("owner_id", true),
                    field("name", true)));
        });

        var result = adapter.catalog(request(VIEW));

        assertThat(result).isEqualTo(new AiRecordPolicyCatalogFacade.Result(
                "work_order", "41", 7L, Set.of("name", "owner_id")));
        assertThat(seenSession.get()).isEqualTo(
                new RuntimeSession(0L, 11L, 17L, 13L, VIEW));
        assertThat(seenModule.get()).isEqualTo("work_order");
        assertThat(AiRecordPolicyCatalogAdapter.class.getMethod(
                        "catalog", AiRecordPolicyCatalogFacade.Request.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    void deniesMissingShellOrModuleViewBeforeReadingSchema() {
        var calls = new AtomicInteger();
        var adapter = new AiRecordPolicyCatalogAdapter((session, moduleCode) -> {
            calls.incrementAndGet();
            return schema(List.of());
        });

        assertDenied(() -> adapter.catalog(request(Set.of("module.work_order.view"))));
        assertDenied(() -> adapter.catalog(request(Set.of("system.runtime.access"))));
        assertThat(calls).hasValue(0);
    }

    @Test
    void propagatesAuthoritativeSchemaFailuresUnchanged() {
        var failure = new BusinessException(
                "MODULE_NOT_PUBLISHED", "missing", HttpStatus.NOT_FOUND);
        var adapter = new AiRecordPolicyCatalogAdapter((session, moduleCode) -> {
            throw failure;
        });

        assertThatThrownBy(() -> adapter.catalog(request(VIEW))).isSameAs(failure);
    }

    private static AiRecordPolicyCatalogFacade.Request request(Set<String> permissions) {
        return new AiRecordPolicyCatalogFacade.Request(
                11L, 13L, 17L, permissions, "work_order");
    }

    private static RecordRuntimeViews.RecordSchema schema(
            List<RecordRuntimeViews.FieldCapability> fields) {
        return new RecordRuntimeViews.RecordSchema(
                "41", "31", "31", "checksum", "READY", null, 7L,
                fields, List.of(), new RecordRuntimeViews.QueryLimits(20, 200, 3));
    }

    private static RecordRuntimeViews.FieldCapability field(
            String fieldCode,
            boolean readable
    ) {
        return new RecordRuntimeViews.FieldCapability(
                fieldCode, fieldCode, fieldCode, "TEXT", "OPTIONAL",
                readable, false, false, false, false,
                List.of(), false, true, true, List.of(), null);
    }

    private static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("PERMISSION_DENIED");
                    assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }
}
