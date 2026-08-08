package com.unique.examine.module.report.controller;

import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.report.api.ReportMapping;
import com.unique.examine.module.report.api.ReportRequests;
import com.unique.examine.module.report.domain.ReportCheckReport;
import com.unique.examine.module.report.domain.ReportDefinition;
import com.unique.examine.module.report.domain.ReportDraft;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.domain.ReportFieldPin;
import com.unique.examine.module.report.domain.ReportSourcePin;
import com.unique.examine.module.report.domain.ReportVersion;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportApiContractTest {
    private static final Instant NOW =
            Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void adminRoutesExposeTheCompleteDefinitionLifecycle() {
        assertThat(ReportAdminController.class
                .getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/v1/systems/{systemId}/admin/reports");
        assertThat(path(method("list"), GetMapping.class)).isEmpty();
        assertThat(path(method("create"), PostMapping.class)).isEmpty();
        assertThat(path(method("detail"), GetMapping.class))
                .containsExactly("/{reportId}");
        assertThat(path(method("saveDraft"), PutMapping.class))
                .containsExactly("/{reportId}/draft");
        assertThat(path(method("check"), PostMapping.class))
                .containsExactly("/{reportId}/draft:check");
        assertThat(path(method("publish"), PostMapping.class))
                .containsExactly("/{reportId}/draft:publish");
        assertThat(path(method("versions"), GetMapping.class))
                .containsExactly("/{reportId}/versions");
        assertThat(path(method("version"), GetMapping.class))
                .containsExactly("/{reportId}/versions/{versionNumber}");
    }

    @Test
    void adminActorReusesConfigurationAuthorityAndRequiresTenant() {
        assertThatThrownBy(() -> ReportAdminController.actor(
                session(20L, Set.of("system.admin.access")), 10))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("PERMISSION_DENIED"));
        assertThatThrownBy(() -> ReportAdminController.actor(
                session(null, permissions()), 10))
                .isInstanceOfSatisfying(ReportException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("REPORT_TENANT_REQUIRED"));

        assertThat(ReportAdminController.actor(
                session(20L, permissions()), 10))
                .extracting(actor -> actor.systemId(), actor -> actor.tenantId(),
                        actor -> actor.memberId())
                .containsExactly(10L, 20L, 30L);
    }

    @Test
    void mappingKeepsDecimalStringIdentitiesAndOrderedPins() {
        var request = new ReportRequests.Create(
                "salesReport", "Sales report", null, "100",
                List.of("amount", "title"));
        var draft = ReportMapping.draft(request);
        var root = ReportDefinition.create(
                101, 10, 20, request.code(), request.name(),
                request.description(), draft, NOW);
        var source = new ReportSourcePin(
                100, "orders", "Orders", 501, 2, 300,
                "orders_module", "schema-2", List.of(
                new ReportFieldPin(902, "amount", "Amount",
                        "NUMBER", "NUMBER"),
                new ReportFieldPin(901, "title", "Title",
                        "TEXT", "TEXT")));
        var version = new ReportVersion(
                601, 101, 10, 20, 1, 1, "salesReport",
                "Sales report", null, source, "a".repeat(64), 30, NOW);
        var check = new ReportCheckReport(
                101, 1, new ReportCheckReport.SourceCapability(
                100, "orders", "Orders", 501, 2, 300,
                "orders_module", "schema-2", List.of(
                new ReportCheckReport.FieldCapability(
                        "902", "amount", "Amount", "NUMBER", "NUMBER", true))),
                List.of());

        assertThat(ReportMapping.definition(root).draft().outputFieldCodes())
                .containsExactly("amount", "title");
        assertThat(ReportMapping.check(check).source())
                .satisfies(value -> {
                    assertThat(value.dataSourceId()).isEqualTo("100");
                    assertThat(value.moduleId()).isEqualTo("300");
                    assertThat(value.fields().getFirst().logicalFieldId())
                            .isEqualTo("902");
                });
        assertThat(ReportMapping.version(version, 601L))
                .satisfies(value -> {
                    assertThat(value.id()).isEqualTo("601");
                    assertThat(value.dataSourceVersionId()).isEqualTo("501");
                    assertThat(value.active()).isTrue();
                    assertThat(value.fields()).extracting(field -> field.code())
                            .containsExactly("amount", "title");
                });
    }

    private static Method method(String name) {
        return java.util.Arrays.stream(ReportAdminController.class
                        .getDeclaredMethods())
                .filter(value -> value.getName().equals(name))
                .findFirst().orElseThrow();
    }

    private static String[] path(
            Method method,
            Class<? extends java.lang.annotation.Annotation> type
    ) {
        var annotation = method.getAnnotation(type);
        if (annotation instanceof GetMapping mapping) {
            return mapping.value();
        }
        if (annotation instanceof PostMapping mapping) {
            return mapping.value();
        }
        if (annotation instanceof PutMapping mapping) {
            return mapping.value();
        }
        throw new AssertionError("Unsupported mapping annotation");
    }

    private static Set<String> permissions() {
        return Set.of("system.admin.access", "module.config.manage");
    }

    private static RequestSession session(
            Long tenantId,
            Set<String> permissions
    ) {
        return new RequestSession() {
            @Override public long sessionId() { return 1; }
            @Override public long accountId() { return 2; }
            @Override public ContextType contextType() {
                return ContextType.SYSTEM;
            }
            @Override public Long systemId() { return 10L; }
            @Override public Long tenantId() { return tenantId; }
            @Override public Long memberId() { return 30L; }
            @Override public long permissionVersion() { return 1; }
            @Override public Set<String> permissions() { return permissions; }
        };
    }
}
