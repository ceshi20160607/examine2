package com.unique.examine.module.report.scheduling;

import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.report.domain.ReportDefinition;
import com.unique.examine.module.report.domain.ReportDraft;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.domain.ReportFieldPin;
import com.unique.examine.module.report.domain.ReportSourcePin;
import com.unique.examine.module.report.domain.ReportVersion;
import com.unique.examine.module.report.port.ReportRepository;
import com.unique.examine.module.report.port.ReportSourceCatalog;
import com.unique.examine.module.report.service.ReportService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportScheduleServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T01:00:00Z");

    @Test
    void createsUpdatesAndDisablesWithOptimisticVersioning() {
        var persistence = new SchedulePersistence();
        var service = service(persistence);

        var created = service.create(session(), 3,
                new ReportScheduleViews.CreateRequest(
                        "daily_ops", "Daily operations", true, "UTC",
                        new ReportScheduleViews.Cadence(
                                "DAILY", "09:00", List.of()),
                        List.of("102", "101")));
        assertThat(created.version()).isEqualTo(1);
        assertThat(created.nextFireAt())
                .isEqualTo("2026-08-04T09:00:00Z");
        assertThat(created.recipientMemberIds())
                .containsExactly("101", "102");

        assertThatThrownBy(() -> service.update(session(), 3,
                Long.parseLong(created.id()),
                new ReportScheduleViews.UpdateRequest(
                        9, "Weekly operations", "UTC",
                        new ReportScheduleViews.Cadence(
                                "WEEKLY", "10:30", List.of("FRIDAY")),
                        List.of("101"))))
                .isInstanceOfSatisfying(ReportException.class,
                        error -> assertThat(error.code()).isEqualTo(
                                "REPORT_SCHEDULE_VERSION_CONFLICT"));

        var updated = service.update(session(), 3,
                Long.parseLong(created.id()),
                new ReportScheduleViews.UpdateRequest(
                        1, "Weekly operations", "UTC",
                        new ReportScheduleViews.Cadence(
                                "WEEKLY", "10:30", List.of("FRIDAY")),
                        List.of("101")));
        assertThat(updated.code()).isEqualTo("daily_ops");
        assertThat(updated.cadence().kind()).isEqualTo("WEEKLY");
        assertThat(updated.version()).isEqualTo(2);

        var disabled = service.setEnabled(session(), 3,
                Long.parseLong(created.id()),
                new ReportScheduleViews.ToggleRequest(2), false);
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.nextFireAt()).isNull();
        assertThat(disabled.version()).isEqualTo(3);
    }

    @Test
    void previewUsesTheScopedPublishedReportAndRejectsBadZones() {
        var service = service(new SchedulePersistence());
        var preview = service.preview(session(), 3,
                new ReportScheduleViews.PreviewRequest(
                        "Asia/Shanghai", new ReportScheduleViews.Cadence(
                        "DAILY", "09:00", List.of()), null));
        assertThat(preview.nextFireAt())
                .isEqualTo("2026-08-05T01:00:00Z");

        assertThatThrownBy(() -> service.preview(session(), 99,
                new ReportScheduleViews.PreviewRequest(
                        "UTC", new ReportScheduleViews.Cadence(
                        "DAILY", "09:00", List.of()), null)))
                .isInstanceOfSatisfying(ReportException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("REPORT_NOT_FOUND"));
        assertThatThrownBy(() -> service.preview(session(), 3,
                new ReportScheduleViews.PreviewRequest(
                        "GMT", new ReportScheduleViews.Cadence(
                        "DAILY", "09:00", List.of()), null)))
                .isInstanceOf(ReportException.class);
    }

    private static ReportScheduleService service(
            SchedulePersistence persistence
    ) {
        RuntimeActiveMemberFacade active = (system, tenant, member) ->
                Optional.of(new RuntimeActiveMemberFacade.ActiveMember(
                        member, null));
        return new ReportScheduleService(persistence.proxy(), reports(),
                active, new IdService(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ConfigSession session() {
        return new ConfigSession(9, 1, 8, 2L,
                Set.of("system.admin.access", "module.config.manage"));
    }

    private static ReportService reports() {
        var root = new ReportDefinition(3, 1, 2, "ops_report",
                "Operations", null, new ReportDraft(30, List.of("status")),
                1, 20L, 1, NOW, NOW, 2);
        var version = new ReportVersion(20, 3, 1, 2, 1, 1,
                "ops_report", "Operations", null,
                new ReportSourcePin(30, "ops_source", "Operations source",
                        40, 1, 50, "work_order", "schema-1", List.of(
                        new ReportFieldPin(101, "status", "Status",
                                "STATUS", "STATUS"))),
                "a".repeat(64), 8, NOW);
        var repository = (ReportRepository) Proxy.newProxyInstance(
                ReportRepository.class.getClassLoader(),
                new Class<?>[]{ReportRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> (Long) args[2] == root.id()
                            ? Optional.of(root) : Optional.empty();
                    case "findActiveVersion" -> (Long) args[2] == root.id()
                            ? Optional.of(version) : Optional.empty();
                    default -> throw new UnsupportedOperationException(
                            method.getName());
                });
        var sources = (ReportSourceCatalog) Proxy.newProxyInstance(
                ReportSourceCatalog.class.getClassLoader(),
                new Class<?>[]{ReportSourceCatalog.class},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
        return new ReportService(repository, sources,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class SchedulePersistence {
        private final List<ReportScheduleStore.Schedule> values =
                new ArrayList<>();

        private ReportScheduleStore proxy() {
            return (ReportScheduleStore) Proxy.newProxyInstance(
                    ReportScheduleStore.class.getClassLoader(),
                    new Class<?>[]{ReportScheduleStore.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findScheduleByCode" -> values.stream()
                                .filter(value -> value.reportId()
                                        == (Long) args[2])
                                .filter(value -> value.code().equals(args[3]))
                                .findFirst();
                        case "findSchedule" -> values.stream()
                                .filter(value -> value.systemId()
                                        == (Long) args[0])
                                .filter(value -> value.tenantId()
                                        == (Long) args[1])
                                .filter(value -> value.reportId()
                                        == (Long) args[2])
                                .filter(value -> value.id() == (Long) args[3])
                                .findFirst();
                        case "insertSchedule" -> {
                            values.add((ReportScheduleStore.Schedule) args[0]);
                            yield null;
                        }
                        case "updateSchedule" -> {
                            var replacement =
                                    (ReportScheduleStore.Schedule) args[0];
                            values.removeIf(value -> value.id()
                                    == replacement.id());
                            values.add(replacement);
                            yield replacement;
                        }
                        case "countSchedules" -> (long) values.size();
                        case "pageSchedules" -> List.copyOf(values);
                        default -> throw new UnsupportedOperationException(
                                method.getName());
                    });
        }
    }
}
