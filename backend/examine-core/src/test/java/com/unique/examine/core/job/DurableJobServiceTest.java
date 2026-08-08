package com.unique.examine.core.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DurableJobServiceTest {
    @Test
    void enqueuePersistsBeforePublishingSignal() {
        var store = new FakeStore();
        var signals = new ArrayList<String>();
        var service = service(store, (id, type) -> {
            assertThat(store.find(id)).isPresent();
            signals.add(id + ":" + type);
        });

        var job = service.enqueue(command(3));

        assertThat(job.status()).isEqualTo("QUEUED");
        assertThat(signals).containsExactly("101:MODULE_IMPORT_PREVIEW");
    }

    @Test
    void claimUsesVersionCasAndSuccessIsTerminal() {
        var store = new FakeStore();
        var service = service(store, (id, type) -> { });
        var queued = service.enqueue(command(3));

        var claimed = service.claim(queued.jobType(), Duration.ofSeconds(30)).orElseThrow();
        var noSecondClaim = service.claim(queued.jobType(), Duration.ofSeconds(30));
        var succeeded = service.succeed(claimed.id(), claimed.version(), Map.of("rows", 2));

        assertThat(claimed.status()).isEqualTo("RUNNING");
        assertThat(claimed.attemptCount()).isEqualTo(1);
        assertThat(noSecondClaim).isEmpty();
        assertThat(succeeded.status()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.progressPercent()).isEqualTo(100);
        assertThat(succeeded.result()).containsEntry("rows", 2);
    }

    @Test
    void failuresRetryOnlyToConfiguredMaximum() {
        var store = new FakeStore();
        var service = service(store, (id, type) -> { });
        var queued = service.enqueue(command(2));

        var first = service.claim(queued.jobType(), Duration.ofSeconds(1)).orElseThrow();
        var retry = service.fail(first.id(), first.version(), "temporary", Duration.ZERO);
        var second = service.claim(queued.jobType(), Duration.ofSeconds(1)).orElseThrow();
        var terminal = service.fail(second.id(), second.version(), "still broken", Duration.ZERO);

        assertThat(retry.status()).isEqualTo("QUEUED");
        assertThat(terminal.status()).isEqualTo("FAILED");
        assertThat(terminal.attemptCount()).isEqualTo(2);
        assertThat(terminal.lastError()).isEqualTo("still broken");
    }

    private static DurableJobService service(FakeStore store, JobSignalPublisher publisher) {
        return new DurableJobService(store, new IdService() {
            @Override public long nextId() { return 101; }
        }, new ObjectMapper(), List.of(publisher));
    }

    private static DurableJobFacade.EnqueueCommand command(int attempts) {
        return new DurableJobFacade.EnqueueCommand("MODULE_IMPORT_PREVIEW", "MODULE_IMPORT_BATCH", "201",
                1L, 2L, 3L, Map.of("batchId", "201"), attempts);
    }

    private static final class FakeStore implements JobStore {
        private final Map<Long, DurableJobFacade.JobRecord> values = new LinkedHashMap<>();

        @Override public void insert(DurableJobFacade.JobRecord job) { values.put(job.id(), job); }

        @Override
        public List<DurableJobFacade.JobRecord> claimCandidates(String type, LocalDateTime now, int limit) {
            return values.values().stream().filter(job -> job.jobType().equals(type))
                    .filter(job -> "QUEUED".equals(job.status()) && !job.availableAt().isAfter(now)
                            || "RUNNING".equals(job.status()) && job.leaseUntil().isBefore(now))
                    .limit(limit).toList();
        }

        @Override
        public Optional<DurableJobFacade.JobRecord> claim(
                long id, long version, LocalDateTime now, LocalDateTime leaseUntil) {
            var current = values.get(id);
            if (current == null || current.version() != version || current.attemptCount() >= current.maxAttempts()
                    || !("QUEUED".equals(current.status()) && !current.availableAt().isAfter(now)
                    || "RUNNING".equals(current.status()) && current.leaseUntil().isBefore(now))) return Optional.empty();
            var claimed = copy(current, "RUNNING", Math.max(1, current.progressPercent()), current.result(), null,
                    current.attemptCount() + 1, current.availableAt(), leaseUntil,
                    current.startedAt() == null ? now : current.startedAt(), null, version + 1);
            values.put(id, claimed);
            return Optional.of(claimed);
        }

        @Override
        public Optional<DurableJobFacade.JobRecord> finish(long id, long version, String status, int progress,
                String resultJson, String error, LocalDateTime availableAt, LocalDateTime finishedAt, LocalDateTime now) {
            var current = values.get(id);
            if (current == null || current.version() != version || !"RUNNING".equals(current.status())) {
                return Optional.empty();
            }
            Map<String, Object> result = resultJson == null ? Map.of() : read(resultJson);
            var changed = copy(current, status, progress, result, error, current.attemptCount(), availableAt,
                    null, current.startedAt(), finishedAt, version + 1);
            values.put(id, changed);
            return Optional.of(changed);
        }

        @Override public Optional<DurableJobFacade.JobRecord> find(long id) { return Optional.ofNullable(values.get(id)); }

        private static DurableJobFacade.JobRecord copy(DurableJobFacade.JobRecord job, String status, int progress,
                Map<String, Object> result, String error, int attempts, LocalDateTime availableAt,
                LocalDateTime leaseUntil, LocalDateTime startedAt, LocalDateTime finishedAt, long version) {
            return new DurableJobFacade.JobRecord(job.id(), job.jobType(), job.ownerType(), job.ownerId(),
                    job.systemId(), job.tenantId(), job.requestedBy(), status, progress, job.input(), result,
                    attempts, job.maxAttempts(), availableAt, leaseUntil, error, startedAt, finishedAt,
                    job.createdAt(), LocalDateTime.now(), version);
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> read(String json) {
            try { return new ObjectMapper().readValue(json, Map.class); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        }
    }
}
