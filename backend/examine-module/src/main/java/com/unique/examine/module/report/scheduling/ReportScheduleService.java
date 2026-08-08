package com.unique.examine.module.report.scheduling;

import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.report.domain.ReportActor;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class ReportScheduleService {
    private static final DateTimeFormatter LOCAL_TIME =
            DateTimeFormatter.ofPattern("HH:mm");

    private final ReportScheduleStore store;
    private final ReportService reports;
    private final RuntimeActiveMemberFacade activeMembers;
    private final IdService ids;
    private final Clock clock;

    @Autowired
    public ReportScheduleService(
            ReportScheduleStore store,
            ReportService reports,
            RuntimeActiveMemberFacade activeMembers,
            IdService ids
    ) {
        this(store, reports, activeMembers, ids, Clock.systemUTC());
    }

    ReportScheduleService(
            ReportScheduleStore store,
            ReportService reports,
            RuntimeActiveMemberFacade activeMembers,
            IdService ids,
            Clock clock
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.reports = Objects.requireNonNull(reports, "reports");
        this.activeMembers = Objects.requireNonNull(activeMembers,
                "activeMembers");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public ReportScheduleViews.Schedule create(
            ConfigSession session,
            long reportId,
            ReportScheduleViews.CreateRequest request
    ) {
        var tenantId = tenant(session);
        if (request == null) {
            throw invalid("Schedule request is required");
        }
        var report = reports.active(actor(session), reportId);
        var code = code(request.code());
        if (store.findScheduleByCode(session.systemId(), tenantId,
                reportId, code).isPresent()) {
            throw new ReportException("REPORT_SCHEDULE_CODE_CONFLICT",
                    "Schedule code is already in use for this report");
        }
        var cadence = cadence(request.cadence());
        var zone = ReportScheduleTiming.zone(request.timeZone());
        var recipients = recipients(session, request.recipientMemberIds());
        var now = clock.instant();
        var value = new ReportScheduleStore.Schedule(
                ids.nextId(), session.systemId(), tenantId, reportId,
                report.root().code(), code, name(request.name()), zone.getId(),
                cadence.kind(), cadence.localTime(), cadence.daysOfWeek(),
                recipients, request.enabled(), session.accountId(),
                session.memberId(), request.enabled()
                ? cadence.nextAfter(now, zone) : null, null, now, now, 1);
        store.insertSchedule(value);
        return view(value);
    }

    @Transactional(readOnly = true)
    public ReportScheduleViews.SchedulePage list(
            ConfigSession session,
            long reportId,
            int page,
            int size
    ) {
        page(page, size);
        var tenantId = tenant(session);
        reports.active(actor(session), reportId);
        var total = store.countSchedules(
                session.systemId(), tenantId, reportId);
        var offset = Math.multiplyExact((long) page - 1, size);
        var items = store.pageSchedules(session.systemId(), tenantId,
                        reportId, size, offset).stream()
                .map(ReportScheduleService::view).toList();
        return new ReportScheduleViews.SchedulePage(
                items, page, size, total);
    }

    @Transactional(readOnly = true)
    public ReportScheduleViews.Schedule detail(
            ConfigSession session,
            long reportId,
            long scheduleId
    ) {
        reports.active(actor(session), reportId);
        return view(require(session, reportId, scheduleId));
    }

    @Transactional
    public ReportScheduleViews.Schedule update(
            ConfigSession session,
            long reportId,
            long scheduleId,
            ReportScheduleViews.UpdateRequest request
    ) {
        if (request == null || request.expectedVersion() <= 0) {
            throw invalid("Schedule expectedVersion is required");
        }
        reports.active(actor(session), reportId);
        var current = require(session, reportId, scheduleId);
        requireVersion(current, request.expectedVersion());
        var cadence = cadence(request.cadence());
        var zone = ReportScheduleTiming.zone(request.timeZone());
        var recipients = recipients(session, request.recipientMemberIds());
        var now = clock.instant();
        var replacement = new ReportScheduleStore.Schedule(
                current.id(), current.systemId(), current.tenantId(),
                current.reportId(), current.reportCode(), current.code(),
                name(request.name()), zone.getId(), cadence.kind(),
                cadence.localTime(), cadence.daysOfWeek(), recipients,
                current.enabled(), current.ownerAccountId(),
                current.ownerMemberId(), current.enabled()
                ? cadence.nextAfter(now, zone) : null,
                current.lastScheduledAt(), current.createdAt(), now,
                current.version() + 1);
        return view(store.updateSchedule(replacement,
                request.expectedVersion()));
    }

    @Transactional
    public ReportScheduleViews.Schedule setEnabled(
            ConfigSession session,
            long reportId,
            long scheduleId,
            ReportScheduleViews.ToggleRequest request,
            boolean enabled
    ) {
        if (request == null || request.expectedVersion() <= 0) {
            throw invalid("Schedule expectedVersion is required");
        }
        reports.active(actor(session), reportId);
        var current = require(session, reportId, scheduleId);
        requireVersion(current, request.expectedVersion());
        if (current.enabled() == enabled) {
            return view(current);
        }
        var now = clock.instant();
        var next = enabled ? current.cadence().nextAfter(now,
                ReportScheduleTiming.zone(current.timeZone())) : null;
        var replacement = new ReportScheduleStore.Schedule(
                current.id(), current.systemId(), current.tenantId(),
                current.reportId(), current.reportCode(), current.code(),
                current.name(), current.timeZone(), current.cadenceKind(),
                current.localTime(), current.daysOfWeek(),
                current.recipientMemberIds(), enabled,
                current.ownerAccountId(), current.ownerMemberId(), next,
                current.lastScheduledAt(), current.createdAt(), now,
                current.version() + 1);
        return view(store.updateSchedule(replacement,
                request.expectedVersion()));
    }

    public ReportScheduleViews.Preview preview(
            ConfigSession session,
            long reportId,
            ReportScheduleViews.PreviewRequest request
    ) {
        reports.active(actor(session), reportId);
        if (request == null) {
            throw invalid("Schedule preview request is required");
        }
        var cadence = cadence(request.cadence());
        var zone = ReportScheduleTiming.zone(request.timeZone());
        var from = instant(request.from());
        var next = cadence.nextAfter(from, zone);
        var local = next.atZone(zone);
        return new ReportScheduleViews.Preview(next.toString(),
                local.toLocalDateTime().toString(),
                local.getOffset().toString(), zone.getId());
    }

    static ReportScheduleViews.Schedule view(
            ReportScheduleStore.Schedule value
    ) {
        return new ReportScheduleViews.Schedule(
                Long.toString(value.id()), Long.toString(value.reportId()),
                value.code(), value.name(), value.enabled(), value.timeZone(),
                new ReportScheduleViews.Cadence(value.cadenceKind().name(),
                        LOCAL_TIME.format(value.localTime()),
                        value.daysOfWeek().stream().map(Enum::name).toList()),
                value.recipientMemberIds().stream().map(String::valueOf).toList(),
                Long.toString(value.ownerMemberId()),
                value.nextFireAt() == null ? null
                        : value.nextFireAt().toString(), value.version(),
                value.createdAt().toString(), value.updatedAt().toString());
    }

    private ReportScheduleStore.Schedule require(
            ConfigSession session,
            long reportId,
            long scheduleId
    ) {
        if (scheduleId <= 0) {
            throw notFound();
        }
        return store.findSchedule(session.systemId(), tenant(session),
                        reportId, scheduleId)
                .orElseThrow(ReportScheduleService::notFound);
    }

    private List<Long> recipients(
            ConfigSession session,
            List<String> values
    ) {
        if (values == null || values.isEmpty()
                || values.size() > ReportScheduleStore.MAX_RECIPIENTS) {
            throw invalid("Schedule requires 1..50 recipient members");
        }
        final List<Long> result;
        try {
            result = values.stream().map(value -> {
                if (value == null || !value.matches("^[1-9][0-9]{0,18}$")) {
                    throw new IllegalArgumentException();
                }
                return Long.parseLong(value);
            }).distinct().sorted().toList();
        } catch (RuntimeException malformed) {
            throw invalid("Schedule recipient member ids are malformed");
        }
        if (result.size() != values.size()) {
            throw invalid("Schedule recipient member ids must be unique");
        }
        for (var memberId : result) {
            if (activeMembers.lockActiveMember(session.systemId(),
                    tenant(session), memberId).isEmpty()) {
                throw invalid("Schedule recipient is not active in this tenant");
            }
        }
        return result;
    }

    static ReportScheduleTiming.Cadence cadence(
            ReportScheduleViews.Cadence value
    ) {
        if (value == null || value.kind() == null
                || value.localTime() == null) {
            throw invalid("Schedule cadence is required");
        }
        try {
            var kind = ReportScheduleTiming.Kind.valueOf(
                    value.kind().strip().toUpperCase(Locale.ROOT));
            var localTime = LocalTime.parse(value.localTime(), LOCAL_TIME);
            var days = value.daysOfWeek().stream().map(day ->
                    DayOfWeek.valueOf(day.strip().toUpperCase(Locale.ROOT)))
                    .toList();
            return new ReportScheduleTiming.Cadence(kind, localTime, days);
        } catch (ReportException known) {
            throw known;
        } catch (RuntimeException malformed) {
            throw invalid("Schedule cadence is malformed");
        }
    }

    private Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return clock.instant();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException malformed) {
            throw invalid("Schedule preview from must be an ISO instant");
        }
    }

    private static String code(String value) {
        if (value == null
                || !value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw invalid("Schedule code is invalid");
        }
        return value;
    }

    private static String name(String value) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw invalid("Schedule name must contain 1..200 characters");
        }
        return value.strip();
    }

    private static void page(int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw invalid("Schedule page must be positive and size must be 1..50");
        }
    }

    private static void requireVersion(
            ReportScheduleStore.Schedule current,
            long expectedVersion
    ) {
        if (current.version() != expectedVersion) {
            throw new ReportException("REPORT_SCHEDULE_VERSION_CONFLICT",
                    "Schedule was changed by another administrator");
        }
    }

    private static ReportActor actor(ConfigSession session) {
        return new ReportActor(session.systemId(), tenant(session),
                session.memberId());
    }

    private static long tenant(ConfigSession session) {
        if (session == null || session.systemId() <= 0
                || session.accountId() <= 0 || session.memberId() <= 0
                || session.tenantId() == null || session.tenantId() <= 0) {
            throw new ReportException("REPORT_TENANT_REQUIRED",
                    "Select an active tenant before managing report schedules");
        }
        return session.tenantId();
    }

    private static ReportException invalid(String message) {
        return new ReportException("REPORT_SCHEDULE_INVALID", message);
    }

    private static ReportException notFound() {
        return new ReportException("REPORT_SCHEDULE_NOT_FOUND",
                "Report schedule does not exist");
    }
}
