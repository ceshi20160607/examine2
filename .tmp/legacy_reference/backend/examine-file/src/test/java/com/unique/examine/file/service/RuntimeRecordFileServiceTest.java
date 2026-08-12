package com.unique.examine.file.service;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import com.unique.examine.file.adapter.memory.InMemoryFileAssetRepository;
import com.unique.examine.file.adapter.memory.InMemoryFileContentStore;
import com.unique.examine.file.domain.FileAsset;
import com.unique.examine.file.domain.FileDomainException;
import com.unique.examine.file.domain.RuntimeRecordFileActor;
import com.unique.examine.file.domain.RuntimeRecordFilePage;
import com.unique.examine.file.port.FileAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeRecordFileServiceTest {
    private static final Set<String> VIEW = Set.of(
            "system.runtime.access",
            "module.work_order.view");
    private static final Set<String> FULL = Set.of(
            "system.runtime.access",
            "module.work_order.view",
            "file.create",
            "file.read",
            "file.reference");
    private static final Instant NOW = Instant.parse("2026-07-27T01:00:00Z");

    private InMemoryFileAssetRepository repository;
    private InMemoryFileContentStore contentStore;
    private FileAssetService assets;
    private RuntimeRecordFileService service;
    private List<RuntimeRecordAccessFacade.RuntimeRecordAccessRequest> accessRequests;

    @BeforeEach
    void setUp() {
        repository = new InMemoryFileAssetRepository();
        contentStore = new InMemoryFileContentStore();
        assets = new FileAssetService(repository, contentStore, fixedClock());
        accessRequests = new ArrayList<>();
        service = new RuntimeRecordFileService(assets, repository, request -> {
            accessRequests.add(request);
            return new RuntimeRecordAccessFacade.RuntimeRecordAccess(
                    Long.toString(request.recordId()),
                    7,
                    true);
        });
    }

    @Test
    void attachesListsDownloadsAndDetachesOnlyTheCanonicalRecordReference() {
        var actor = actor(42, 100, FULL);
        var first = service.attach(actor, "one.txt", "text/plain", bytes("one"));
        var second = service.attach(actor, "two.txt", "text/plain", bytes("two"));
        var third = service.attach(actor, "three.txt", "text/plain", bytes("three"));

        var page = service.page(actor, 1, 2);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).extracting(item -> item.asset().id())
                .containsExactly(third.asset().id(), second.asset().id());
        assertThat(page.items()).allSatisfy(item -> {
            assertThat(item.reference().target())
                    .isEqualTo(new AggregateRef("RUNTIME_RECORD", "42"));
            assertThat(item.reference().createdAt()).isEqualTo(NOW);
        });

        var download = service.download(actor, first.asset().id());
        assertThat(download.content()).isEqualTo(bytes("one"));

        assets.addReference(
                actor.fileActor(),
                first.asset().id(),
                new AggregateRef("RUNTIME_RECORD", "99"));
        var detached = service.detach(actor, first.asset().id());
        assertThat(detached.reference().target().id()).isEqualTo("42");

        var persisted = repository.findById(10, 20, first.asset().id()).orElseThrow();
        assertThat(persisted.references().keySet())
                .containsExactly(new AggregateRef("RUNTIME_RECORD", "99"));
        assertThat(contentStore.read(persisted.objectKey()).orElseThrow())
                .isEqualTo(bytes("one"));
        assertThat(service.page(actor, 1, 20).total()).isEqualTo(2);
        assertFileNotFound(() -> service.download(actor, first.asset().id()));

        assertThat(accessRequests).allSatisfy(request -> {
            assertThat(request.moduleCode()).isEqualTo("work_order");
            assertThat(request.effectivePermissions()).containsAll(VIEW);
        });
    }

    @Test
    void readUploadAndDetachApplyFrozenPermissionsAfterCanonicalViewResolution() {
        var owner = actor(42, 100, FULL);
        var attached = service.attach(owner, "one.txt", "text/plain", bytes("one"));

        var missingRead = actor(42, 101, VIEW);
        assertForbidden(() -> service.page(missingRead, 1, 20));
        assertForbidden(() -> service.download(missingRead, attached.asset().id()));

        var missingCreate = actor(
                42,
                101,
                Set.of("system.runtime.access", "module.work_order.view", "file.reference"));
        assertForbidden(() -> service.attach(
                missingCreate,
                "denied.txt",
                "text/plain",
                bytes("denied")));

        var otherCreator = actor(
                42,
                101,
                Set.of("system.runtime.access", "module.work_order.view", "file.reference"));
        assertForbidden(() -> service.detach(otherCreator, attached.asset().id()));

        var manager = actor(
                42,
                102,
                Set.of("system.runtime.access", "module.work_order.view", "file.manage"));
        service.detach(manager, attached.asset().id());
        assertThat(repository.findById(10, 20, attached.asset().id())).isPresent();

        assertThat(accessRequests).hasSize(6);
    }

    @Test
    void fileFromAnotherRecordOrTenantUsesNotFoundSemantics() {
        var attached = service.attach(
                actor(42, 100, FULL),
                "one.txt",
                "text/plain",
                bytes("one"));

        assertFileNotFound(() -> service.download(
                actor(43, 100, FULL),
                attached.asset().id()));
        assertFileNotFound(() -> service.download(
                new RuntimeRecordFileActor(10, 21, 100, FULL, "work_order", 42),
                attached.asset().id()));
    }

    @Test
    void downloadRejectsStoredContentWhoseLengthOrSha256NoLongerMatchesMetadata() {
        var actor = actor(42, 100, FULL);
        var shaMismatch = service.attach(actor, "sha.txt", "text/plain", bytes("one"));
        contentStore.delete(shaMismatch.asset().objectKey());
        contentStore.put(shaMismatch.asset().objectKey(), bytes("two"));

        assertThatThrownBy(() -> service.download(actor, shaMismatch.asset().id()))
                .isInstanceOfSatisfying(
                        FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_CONTENT_INTEGRITY_FAILED"));

        var lengthMismatch = service.attach(actor, "size.txt", "text/plain", bytes("size"));
        contentStore.delete(lengthMismatch.asset().objectKey());
        contentStore.put(lengthMismatch.asset().objectKey(), bytes("different-size"));

        assertThatThrownBy(() -> service.download(actor, lengthMismatch.asset().id()))
                .isInstanceOfSatisfying(
                        FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_CONTENT_INTEGRITY_FAILED"));
    }

    @Test
    void viewFailurePrecedesFilePersistenceAndDoesNotLeakRecordExistence() {
        var deniedRepository = new InMemoryFileAssetRepository();
        var deniedStore = new InMemoryFileContentStore();
        var deniedAssets = new FileAssetService(deniedRepository, deniedStore, fixedClock());
        var denied = new RuntimeRecordFileService(
                deniedAssets,
                deniedRepository,
                request -> {
                    throw new BusinessException(
                            "RECORD_NOT_FOUND",
                            "missing or outside VIEW scope",
                            HttpStatus.NOT_FOUND);
                });

        assertThatThrownBy(() -> denied.attach(
                actor(42, 100, FULL),
                "hidden.txt",
                "text/plain",
                bytes("hidden")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("RECORD_NOT_FOUND"));
        assertThat(deniedRepository.findById(10, 20, 1)).isEmpty();
    }

    @Test
    void failedReferenceCreationCompensatesTheNewMetadataAndContentObject() {
        var delegate = new InMemoryFileAssetRepository();
        var failingRepository = new FailingAttachRepository(delegate);
        var store = new InMemoryFileContentStore();
        var failingAssets = new FileAssetService(failingRepository, store, fixedClock());
        var failingService = new RuntimeRecordFileService(
                failingAssets,
                failingRepository,
                request -> new RuntimeRecordAccessFacade.RuntimeRecordAccess("42", 1, true));

        assertThatThrownBy(() -> failingService.attach(
                actor(42, 100, FULL),
                "failed.txt",
                "text/plain",
                bytes("failed")))
                .isInstanceOfSatisfying(
                        FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("ATTACH_FAILED"));

        assertThat(delegate.findById(10, 20, 1)).isEmpty();
        assertThat(store.read("system/10/tenant/20/file/1")).isEmpty();
    }

    @Test
    void bundleUsesStableOrderSafeFallbackAndDeterministicDuplicateNames()
            throws Exception {
        var actor = actor(42, 100, FULL);
        var first = service.attach(actor, "first.txt", "text/plain", bytes("first"));
        var second = service.attach(actor, "report.txt", "text/plain", bytes("second"));
        var third = service.attach(actor, "third.txt", "text/plain", bytes("third"));
        rewrite(first.asset(), "../folder\u0000/report.txt", first.asset().size());
        rewrite(third.asset(), "../..", third.asset().size());

        var bundle = service.bundle(actor);
        var entries = unzip(bundle.content());

        assertThat(bundle.recordId()).isEqualTo("42");
        assertThat(bundle.fileCount()).isEqualTo(3);
        assertThat(entries.keySet()).containsExactly(
                "file-" + third.asset().id(),
                "report.txt",
                "report-" + first.asset().id() + ".txt");
        assertThat(entries.get("file-" + third.asset().id())).isEqualTo(bytes("third"));
        assertThat(entries.get("report.txt")).isEqualTo(bytes("second"));
        assertThat(entries.get("report-" + first.asset().id() + ".txt"))
                .isEqualTo(bytes("first"));
        assertThat(entries.keySet()).noneMatch(name ->
                name.contains("/") || name.contains("\\") || name.contains("\u0000"));
    }

    @Test
    void emptyRecordReturnsAValidEmptyZip() throws Exception {
        var bundle = service.bundle(actor(42, 100, FULL));

        assertThat(bundle.fileCount()).isZero();
        assertThat(bundle.content()).isNotEmpty();
        assertThat(unzip(bundle.content())).isEmpty();
    }

    @Test
    void bundleRejectsMoreThanFiftyFilesBeforeReadingContent() {
        var actor = actor(42, 100, FULL);
        for (int index = 0; index <= RuntimeRecordFileService.MAX_BUNDLE_FILES; index++) {
            service.attach(
                    actor,
                    "file-" + index + ".txt",
                    "text/plain",
                    bytes("x"));
        }
        var first = repository.findById(10, 20, 1).orElseThrow();
        contentStore.delete(first.objectKey());

        assertThatThrownBy(() -> service.bundle(actor))
                .isInstanceOfSatisfying(
                        FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_BUNDLE_LIMIT"));
    }

    @Test
    void bundleRejectsMetadataOverOneHundredMibBeforeReadingContent() {
        var actor = actor(42, 100, FULL);
        var attached = service.attach(actor, "large.bin", "application/octet-stream", bytes("x"));
        rewrite(
                attached.asset(),
                attached.asset().originalName(),
                RuntimeRecordFileService.MAX_BUNDLE_UNCOMPRESSED_BYTES + 1);
        contentStore.delete(attached.asset().objectKey());

        assertThatThrownBy(() -> service.bundle(actor))
                .isInstanceOfSatisfying(
                        FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_BUNDLE_LIMIT"));
    }

    @Test
    void bundleRejectsAnySha256MismatchBeforeCreatingOutput() {
        var actor = actor(42, 100, FULL);
        service.attach(actor, "valid.txt", "text/plain", bytes("valid"));
        var tampered = service.attach(actor, "tampered.txt", "text/plain", bytes("one"));
        contentStore.delete(tampered.asset().objectKey());
        contentStore.put(tampered.asset().objectKey(), bytes("two"));

        assertThatThrownBy(() -> service.bundle(actor))
                .isInstanceOfSatisfying(
                        FileDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("FILE_CONTENT_INTEGRITY_FAILED"));
    }

    private static RuntimeRecordFileActor actor(
            long recordId,
            long memberId,
            Set<String> permissions
    ) {
        return new RuntimeRecordFileActor(
                10,
                20,
                memberId,
                permissions,
                "work_order",
                recordId);
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private FileAsset rewrite(FileAsset asset, String originalName, long size) {
        return repository.save(new FileAsset(
                asset.id(),
                asset.systemId(),
                asset.tenantId(),
                asset.uploaderMemberId(),
                asset.objectKey(),
                originalName,
                asset.mediaType(),
                size,
                asset.sha256(),
                asset.createdAt(),
                asset.references(),
                asset.version() + 1));
    }

    private static Map<String, byte[]> unzip(byte[] content) throws IOException {
        var entries = new LinkedHashMap<String, byte[]>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            var entry = zip.getNextEntry();
            while (entry != null) {
                entries.put(entry.getName(), zip.readAllBytes());
                zip.closeEntry();
                entry = zip.getNextEntry();
            }
        }
        return entries;
    }

    private static void assertForbidden(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_FORBIDDEN"));
    }

    private static void assertFileNotFound(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_NOT_FOUND"));
    }

    private static final class FailingAttachRepository implements FileAssetRepository {
        private final FileAssetRepository delegate;

        private FailingAttachRepository(FileAssetRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public long nextId() {
            return delegate.nextId();
        }

        @Override
        public Optional<FileAsset> findById(long systemId, long tenantId, long id) {
            return delegate.findById(systemId, tenantId, id);
        }

        @Override
        public RuntimeRecordFilePage findReferencePage(
                long systemId,
                long tenantId,
                AggregateRef target,
                int page,
                int size
        ) {
            return delegate.findReferencePage(systemId, tenantId, target, page, size);
        }

        @Override
        public FileAsset save(FileAsset asset) {
            if (asset.version() > 1) {
                throw new FileDomainException("ATTACH_FAILED", "reference persistence failed");
            }
            return delegate.save(asset);
        }

        @Override
        public void delete(long systemId, long tenantId, long id, long expectedVersion) {
            delegate.delete(systemId, tenantId, id, expectedVersion);
        }
    }
}
