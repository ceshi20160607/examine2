package com.unique.examine.file.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.OutboxEvent;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import com.unique.examine.file.adapter.memory.InMemoryFileAssetRepository;
import com.unique.examine.file.adapter.memory.InMemoryFileContentStore;
import com.unique.examine.file.domain.FileActor;
import com.unique.examine.file.service.FileAssetService;
import com.unique.examine.file.service.RuntimeRecordFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class OpenApiRecordFileFacadeTest {
    private static final Instant NOW = Instant.parse("2026-08-04T01:00:00Z");
    private static final Set<String> FULL = Set.of(
            "system.runtime.access", "module.work_order.view",
            "file.create", "file.reference", "file.read");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private InMemoryFileAssetRepository repository;
    private InMemoryFileContentStore contentStore;
    private FileAssetService assets;
    private RuntimeRecordFileService recordFiles;
    private InMemoryIdempotency idempotency;
    private List<OperationAudit> audits;
    private List<OutboxEvent> events;
    private AccessGate access;
    private OpenApiRecordFileFacade facade;

    @BeforeEach
    void setUp() {
        repository = new InMemoryFileAssetRepository();
        contentStore = new InMemoryFileContentStore();
        assets = new FileAssetService(
                repository, contentStore, Clock.fixed(NOW, ZoneOffset.UTC));
        access = new AccessGate();
        recordFiles = new RuntimeRecordFileService(assets, repository, access);
        idempotency = new InMemoryIdempotency();
        audits = new ArrayList<>();
        events = new ArrayList<>();
        facade = new OpenApiRecordFileFacade(
                recordFiles,
                idempotency,
                new CapturingAudit(audits),
                event -> {
                    events.add(event);
                    return events.size();
                },
                objectMapper);
    }

    @Test
    void uploadReplayIsExactChangedPayloadConflictsAndApplicationsAreIsolated() throws Exception {
        var command = upload("one.txt", "text/plain", bytes("one"));

        var first = facade.upload(session(101, 20, FULL), "work_order", "42", command, "stable-key");
        var replay = facade.upload(session(101, 20, FULL), "work_order", "42", command, "stable-key");
        var changedName = catchThrowableOfType(
                () -> facade.upload(session(101, 20, FULL), "work_order", "42",
                        upload("two.txt", "text/plain", bytes("one")), "stable-key"),
                BusinessException.class);
        var changedMedia = catchThrowableOfType(
                () -> facade.upload(session(101, 20, FULL), "work_order", "42",
                        upload("one.txt", "application/octet-stream", bytes("one")), "stable-key"),
                BusinessException.class);
        var changedBytes = catchThrowableOfType(
                () -> facade.upload(session(101, 20, FULL), "work_order", "42",
                        upload("one.txt", "text/plain", bytes("two")), "stable-key"),
                BusinessException.class);
        var otherApplication = facade.upload(
                session(102, 20, FULL), "work_order", "42",
                upload("two.txt", "text/plain", bytes("two")), "stable-key");

        assertThat(replay).isEqualTo(first);
        assertThat(changedName.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(changedMedia.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(changedBytes.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(otherApplication.fileId()).isNotEqualTo(first.fileId());
        assertThat(facade.list(session(101, 20, FULL), "work_order", "42", 1, 20).total())
                .isEqualTo(2);
        assertThat(idempotency.completions).hasValue(2);
        assertThat(audits).hasSize(2).allSatisfy(audit -> {
            assertThat(audit.actor().sourceType()).isEqualTo("OPENAPI");
            assertThat(audit.action()).isEqualTo("RUNTIME_RECORD_FILE_ATTACHED");
            assertThat(audit.after()).isInstanceOf(OpenApiRecordFileFacade.FileView.class);
        });
        assertThat(events).hasSize(2).allSatisfy(event ->
                assertThat(event.eventType()).isEqualTo("RUNTIME_RECORD_FILE_ATTACHED"));

        var persistedFacts = objectMapper.writeValueAsString(List.of(audits, events, idempotency.responses));
        assertThat(persistedFacts)
                .doesNotContain("objectKey", "system/10/tenant/20/file/", "contentBase64", "b25l");
    }

    @Test
    void base64MustBeCanonicalAndDecodedContentIsBoundedTo512Kib() {
        var invalid = catchThrowableOfType(
                () -> facade.upload(session(101, 20, FULL), "work_order", "42",
                        new OpenApiRecordFileFacade.UploadCommand("bad.bin", "application/octet-stream", "%%"),
                        "invalid"),
                BusinessException.class);
        var nonCanonical = catchThrowableOfType(
                () -> facade.upload(session(101, 20, FULL), "work_order", "42",
                        new OpenApiRecordFileFacade.UploadCommand("bad.bin", "application/octet-stream", "Zg"),
                        "non-canonical"),
                BusinessException.class);
        var overLimit = catchThrowableOfType(
                () -> facade.upload(session(101, 20, FULL), "work_order", "42",
                        upload("large.bin", "application/octet-stream",
                                new byte[OpenApiRecordFileFacade.MAX_DECODED_BYTES + 1]),
                        "large"),
                BusinessException.class);
        var exact = facade.upload(session(101, 20, FULL), "work_order", "42",
                upload("exact.bin", "application/octet-stream",
                        new byte[OpenApiRecordFileFacade.MAX_DECODED_BYTES]),
                "exact");

        assertThat(invalid.code()).isEqualTo("OPENAPI_FILE_CONTENT_INVALID");
        assertThat(nonCanonical.code()).isEqualTo("OPENAPI_FILE_CONTENT_INVALID");
        assertThat(overLimit.code()).isEqualTo("OPENAPI_FILE_SIZE_LIMIT");
        assertThat(exact.sizeBytes()).isEqualTo(OpenApiRecordFileFacade.MAX_DECODED_BYTES);
        assertThat(idempotency.completions).hasValue(1);
    }

    @Test
    void listProjectsNoObjectKeyAndDownloadReturnsVerifiedDefensiveBytesAndSafeHeaders() throws Exception {
        var uploaded = facade.upload(
                session(101, 20, FULL), "work_order", "42",
                upload("report 甲.txt", "not a media type", bytes("verified")), "upload-key");

        var page = facade.list(session(101, 20, FULL), "work_order", "42", 1, 20);
        var download = facade.download(
                session(101, 20, FULL), "work_order", "42", uploaded.fileId());

        assertThat(page.items()).containsExactly(uploaded);
        assertThat(page.items().getFirst().downloadPath()).isEqualTo(
                "/openapi/v1/modules/work_order/records/42/files/" + uploaded.fileId() + "/content");
        assertThat(download.mediaType()).isEqualTo("application/octet-stream");
        assertThat(download.sizeBytes()).isEqualTo(bytes("verified").length);
        assertThat(download.sha256()).isEqualTo(uploaded.sha256());
        assertThat(download.contentDisposition()).contains("attachment", "filename");
        var copy = download.content();
        copy[0] = 0;
        assertThat(download.content()).isEqualTo(bytes("verified"));
        assertThat(objectMapper.writeValueAsString(page)).doesNotContain("objectKey", "system/");

        var asset = repository.findById(10, 20, Long.parseLong(uploaded.fileId())).orElseThrow();
        contentStore.delete(asset.objectKey());
        contentStore.put(asset.objectKey(), bytes("tampered"));
        var corrupted = catchThrowableOfType(
                () -> facade.download(session(101, 20, FULL), "work_order", "42", uploaded.fileId()),
                BusinessException.class);
        assertThat(corrupted.code()).isEqualTo("FILE_CONTENT_INTEGRITY_FAILED");
    }

    @Test
    void currentPermissionsRecordScopeTenantAndExactReferenceRemainAuthoritative() {
        var uploaded = facade.upload(
                session(101, 20, FULL), "work_order", "42",
                upload("one.txt", "text/plain", bytes("one")), "upload-key");
        var readOnly = Set.of("system.runtime.access", "module.work_order.view", "file.read");
        var noRead = Set.of("system.runtime.access", "module.work_order.view");

        var deniedRead = catchThrowableOfType(
                () -> facade.list(session(101, 20, noRead), "work_order", "42", 1, 20),
                BusinessException.class);
        var deniedUpload = catchThrowableOfType(
                () -> facade.upload(session(101, 20, readOnly), "work_order", "42",
                        upload("denied.txt", "text/plain", bytes("denied")), "denied-key"),
                BusinessException.class);
        assertThat(deniedRead.code()).isEqualTo("FILE_FORBIDDEN");
        assertThat(deniedUpload.code()).isEqualTo("FILE_FORBIDDEN");
        assertThat(idempotency.begins).hasValue(1);

        access.failure = new BusinessException(
                "RECORD_NOT_FOUND", "outside current row scope", HttpStatus.NOT_FOUND);
        var hiddenRecord = catchThrowableOfType(
                () -> facade.download(session(101, 20, FULL), "work_order", "42", uploaded.fileId()),
                BusinessException.class);
        assertThat(hiddenRecord.code()).isEqualTo("RECORD_NOT_FOUND");
        access.failure = null;

        var crossTenant = catchThrowableOfType(
                () -> facade.download(session(101, 21, FULL), "work_order", "42", uploaded.fileId()),
                BusinessException.class);
        assertThat(crossTenant.code()).isEqualTo("FILE_NOT_FOUND");

        var unreferenced = assets.register(
                new FileActor(10, 20, 100, Set.of(FileAssetService.CREATE, FileAssetService.READ)),
                "loose.txt", "text/plain", bytes("loose"));
        var hiddenReference = catchThrowableOfType(
                () -> facade.download(session(101, 20, FULL), "work_order", "42",
                        Long.toString(unreferenced.id())),
                BusinessException.class);
        assertThat(hiddenReference.code()).isEqualTo("FILE_NOT_FOUND");
    }

    private static OpenApiRecordFileFacade.Session session(
            long applicationId,
            long tenantId,
            Set<String> permissions
    ) {
        return new OpenApiRecordFileFacade.Session(
                applicationId, 7, 10, tenantId, 100, permissions, "request-72", "trace-72");
    }

    private static OpenApiRecordFileFacade.UploadCommand upload(
            String name,
            String mediaType,
            byte[] content
    ) {
        return new OpenApiRecordFileFacade.UploadCommand(
                name, mediaType, Base64.getEncoder().encodeToString(content));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class AccessGate implements RuntimeRecordAccessFacade {
        private BusinessException failure;

        @Override
        public RuntimeRecordAccess requireView(RuntimeRecordAccessRequest request) {
            if (failure != null) {
                throw failure;
            }
            return new RuntimeRecordAccess(Long.toString(request.recordId()), 1, true);
        }
    }

    private static final class InMemoryIdempotency implements IdempotencyFacade {
        private final Map<String, IdempotencyRecord> records = new HashMap<>();
        private final Map<Long, String> scopesById = new HashMap<>();
        private final List<String> responses = new ArrayList<>();
        private final AtomicInteger begins = new AtomicInteger();
        private final AtomicInteger completions = new AtomicInteger();

        @Override
        public Optional<IdempotencyRecord> find(String scopeType, String scopeKey, String key) {
            return Optional.ofNullable(records.get(scopeType + ":" + scopeKey + ":" + key));
        }

        @Override
        public long begin(String scopeType, String scopeKey, String key, String requestHash, Duration ttl) {
            var id = begins.incrementAndGet();
            var scope = scopeType + ":" + scopeKey + ":" + key;
            records.put(scope, new IdempotencyRecord(id, requestHash, "PROCESSING", null));
            scopesById.put((long) id, scope);
            return id;
        }

        @Override
        public void complete(long id, int httpStatus, String responseCode, String responseBody) {
            completions.incrementAndGet();
            var scope = scopesById.get(id);
            var current = records.get(scope);
            records.put(scope, new IdempotencyRecord(id, current.requestHash(), "COMPLETED", responseBody));
            responses.add(responseBody);
        }
    }

    private record CapturingAudit(List<OperationAudit> values) implements OperationAuditFacade {
        @Override
        public void recordSuccess(OperationAudit audit) {
            values.add(audit);
        }

        @Override
        public void recordDenied(OperationAudit audit) {
            throw new AssertionError("No denied audit expected");
        }

        @Override
        public void recordFailed(OperationAudit audit) {
            throw new AssertionError("No failed audit expected");
        }
    }
}
