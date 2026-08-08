package com.unique.examine.file.service;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.file.adapter.memory.InMemoryFileAssetRepository;
import com.unique.examine.file.adapter.memory.InMemoryFileContentStore;
import com.unique.examine.file.domain.FileActor;
import com.unique.examine.file.domain.FileDomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileAssetServiceTest {
    private FileAssetService service;
    private FileActor uploader;

    @BeforeEach
    void setUp() {
        service = new FileAssetService(new InMemoryFileAssetRepository(), new InMemoryFileContentStore(),
                Clock.fixed(Instant.parse("2026-07-25T10:00:00Z"), ZoneOffset.UTC));
        uploader = actor(100, FileAssetService.CREATE, FileAssetService.READ, FileAssetService.REFERENCE);
    }

    @Test
    void registerReferenceUnreferenceAndDeleteFormAClosedMetadataLifecycle() {
        var bytes = "contract".getBytes(StandardCharsets.UTF_8);
        var asset = service.register(uploader, "contract.txt", "text/plain", bytes);
        assertThat(asset.size()).isEqualTo(bytes.length);
        assertThat(asset.sha256()).hasSize(64);
        assertThat(service.readContent(uploader, asset.id())).isEqualTo(bytes);

        var target = new AggregateRef("MODULE_RECORD", "42");
        asset = service.addReference(uploader, asset.id(), target);
        assertThat(asset.referenceCount()).isEqualTo(1);

        asset = service.removeReference(uploader, asset.id(), target);
        assertThat(asset.referenceCount()).isZero();
        var deletedFileId = asset.id();
        service.delete(uploader, asset.id());

        assertThatThrownBy(() -> service.get(uploader, deletedFileId))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_NOT_FOUND"));
    }

    @Test
    void referencedFileCannotBeDeletedUntilItsReferenceIsRemoved() {
        var asset = service.register(uploader, "image.png", "image/png", new byte[]{1, 2, 3});
        var target = new AggregateRef("MODULE_RECORD", "43");
        service.addReference(uploader, asset.id(), target);

        assertThatThrownBy(() -> service.delete(uploader, asset.id()))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_STILL_REFERENCED"));
        assertThat(service.get(uploader, asset.id()).referenceCount()).isEqualTo(1);
    }

    @Test
    void registrationAndReferencePermissionsAreRequired() {
        assertThatThrownBy(() -> service.register(actor(100), "a.txt", "text/plain", new byte[0]))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_FORBIDDEN"));

        var asset = service.register(uploader, "a.txt", "text/plain", new byte[0]);
        assertThatThrownBy(() -> service.addReference(actor(101), asset.id(),
                new AggregateRef("MODULE_RECORD", "1")))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_FORBIDDEN"));
    }

    @Test
    void referenceOperationsAreIdempotentAndOwnershipIsProtected() {
        var asset = service.register(uploader, "a.txt", "text/plain", new byte[0]);
        var target = new AggregateRef("MODULE_RECORD", "1");
        var referenced = service.addReference(uploader, asset.id(), target);
        assertThat(service.addReference(uploader, asset.id(), target)).isEqualTo(referenced);

        assertThatThrownBy(() -> service.removeReference(
                actor(101, FileAssetService.REFERENCE), asset.id(), target))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_FORBIDDEN"));
        assertThat(service.removeReference(
                actor(102, FileAssetService.MANAGE), asset.id(), target).referenceCount()).isZero();
    }

    @Test
    void anotherTenantCannotObserveOrDeleteTheFileAndOnlyUploaderOrManagerCanDelete() {
        var asset = service.register(uploader, "a.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> service.get(
                new FileActor(10, 21, 100, Set.of()), asset.id()))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_NOT_FOUND"));
        assertThatThrownBy(() -> service.delete(actor(101), asset.id()))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_FORBIDDEN"));
        assertThat(service.delete(actor(102, FileAssetService.MANAGE), asset.id()).id())
                .isEqualTo(asset.id());
    }

    @Test
    void fileNameCannotSmuggleAPath() {
        assertThatThrownBy(() -> service.register(uploader, "../secret.txt", "text/plain", new byte[0]))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_NAME_INVALID"));
    }

    @Test
    void singleUploadSizeIsBounded() {
        assertThatThrownBy(() -> service.register(
                uploader, "large.bin", "application/octet-stream",
                new byte[FileAssetService.MAX_CONTENT_BYTES + 1]))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_SIZE_LIMIT"));
    }

    private static FileActor actor(long memberId, String... permissions) {
        return new FileActor(10, 20, memberId, Set.of(permissions));
    }
}
