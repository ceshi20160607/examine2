package com.unique.examine.file.service;

import com.unique.examine.file.adapter.memory.InMemoryFileContentStore;
import com.unique.examine.file.domain.FileActor;
import com.unique.examine.file.domain.FileDomainException;
import com.unique.examine.file.domain.FileStorageStatus;
import com.unique.examine.file.domain.MultipartUploadSession;
import com.unique.examine.file.support.TestFileAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileAssetAdvancedServiceTest {
    private MutableClock clock;
    private TestFileAssetRepository repository;
    private InMemoryFileContentStore contentStore;
    private FileAssetService service;
    private FileActor owner;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-08-06T06:00:00Z"));
        repository = new TestFileAssetRepository();
        contentStore = new InMemoryFileContentStore();
        service = new FileAssetService(repository, contentStore,
                () -> new FileStorageStatus(FileStorageStatus.Mode.S3, "files-production",
                        FileAssetService.MAX_CONTENT_BYTES, FileAssetService.MAX_MULTIPART_BYTES,
                        FileAssetService.MULTIPART_PART_BYTES, true), clock);
        owner = actor(100, FileAssetService.CREATE, FileAssetService.READ,
                FileAssetService.REFERENCE, FileAssetService.MANAGE);
    }

    @Test
    void listsOnlyTheScopedKeywordAndExactOrPrefixMediaTypePage() {
        service.register(owner, "invoice.pdf", "application/pdf", "%PDF-1.7".getBytes());
        service.register(owner, "team-photo.png", "image/png", png(4, 2));
        service.register(owner, "notes.txt", "text/plain", "notes".getBytes());

        var images = service.list(actor(101, FileAssetService.READ), 1, 10,
                " PHOTO ", "IMAGE/*");
        assertThat(images.items()).extracting(value -> value.originalName())
                .containsExactly("team-photo.png");
        assertThat(images.total()).isOne();
        assertThat(images.totalPages()).isOne();

        assertThat(service.list(actor(101, FileAssetService.READ), 1, 10,
                null, "application/pdf").items())
                .extracting(value -> value.originalName()).containsExactly("invoice.pdf");
        assertThat(service.list(new FileActor(10, 21, 101, Set.of(FileAssetService.READ)),
                1, 10, null, null).total()).isZero();
        assertThatThrownBy(() -> service.list(actor(101, FileAssetService.READ),
                1, 10, null, "image"))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_FILTER_INVALID"));
    }

    @Test
    void previewsOnlyVerifiedImagesAndPdfAndProducesBoundedPngThumbnails() throws Exception {
        var image = service.register(owner, "wide.png", "image/png", png(4, 2));
        var preview = service.preview(owner, image.id());
        assertThat(preview.mediaType()).isEqualTo("image/png");
        assertThat(preview.content()).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e);

        var thumbnail = service.thumbnail(owner, image.id(), 2, 2);
        assertThat(thumbnail.mediaType()).isEqualTo("image/png");
        assertThat(thumbnail.width()).isEqualTo(2);
        assertThat(thumbnail.height()).isEqualTo(1);
        var rendered = ImageIO.read(new java.io.ByteArrayInputStream(thumbnail.content()));
        assertThat(rendered.getWidth()).isEqualTo(2);
        assertThat(rendered.getHeight()).isEqualTo(1);
        var jpeg = service.register(owner, "photo.jpg", "image/jpeg", encoded("jpeg", 3, 2));
        var gif = service.register(owner, "animation.gif", "image/gif", encoded("gif", 3, 2));
        assertThat(service.thumbnail(owner, jpeg.id(), 2, 2).width()).isEqualTo(2);
        assertThat(service.thumbnail(owner, gif.id(), 2, 2).width()).isEqualTo(2);

        var pdf = service.register(owner, "report.pdf", "application/pdf",
                "%PDF-1.7\nminimal".getBytes());
        assertThat(service.preview(owner, pdf.id()).mediaType()).isEqualTo("application/pdf");
        assertThatThrownBy(() -> service.thumbnail(owner, pdf.id(), 100, 100))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_THUMBNAIL_UNSUPPORTED"));

        var text = service.register(owner, "notes.txt", "text/plain", "notes".getBytes());
        assertThatThrownBy(() -> service.preview(owner, text.id()))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_PREVIEW_UNSUPPORTED"));
        var forged = service.register(owner, "forged.png", "image/png", "not-png".getBytes());
        assertThatThrownBy(() -> service.preview(owner, forged.id()))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_CONTENT_TYPE_MISMATCH"));

        var corrupted = service.register(owner, "corrupted.png", "image/png", png(2, 2));
        contentStore.delete(corrupted.objectKey());
        contentStore.put(corrupted.objectKey(), png(1, 1));
        assertThatThrownBy(() -> service.preview(owner, corrupted.id()))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_CONTENT_INTEGRITY_FAILED"));
    }

    @Test
    void multipartIsOutOfOrderBoundedMemberScopedRetryableAndCompletionIdempotent() {
        var content = new byte[FileAssetService.MULTIPART_PART_BYTES + 3];
        java.util.Arrays.fill(content, 0, FileAssetService.MULTIPART_PART_BYTES, (byte) 7);
        content[content.length - 3] = 1;
        content[content.length - 2] = 2;
        content[content.length - 1] = 3;
        var digest = sha256(content);
        var initialized = service.initializeMultipart(owner, "large.bin",
                "application/octet-stream", content.length, digest);

        assertThat(initialized.partSizeBytes()).isEqualTo(5 * 1024 * 1024);
        assertThat(initialized.partCount()).isEqualTo(2);
        var last = new byte[]{1, 2, 3};
        var second = service.uploadPart(owner, initialized.uploadId(), 2, last, sha256(last));
        assertThat(second.replay()).isFalse();
        assertThat(service.uploadPart(owner, initialized.uploadId(), 2, last, null).replay()).isTrue();
        assertThatThrownBy(() -> service.uploadPart(owner, initialized.uploadId(), 2,
                new byte[]{9, 9, 9}, null))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_MULTIPART_PART_CONFLICT"));
        assertThatThrownBy(() -> service.completeMultipart(owner, initialized.uploadId(), digest))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_MULTIPART_INCOMPLETE"));
        assertThatThrownBy(() -> service.uploadPart(actor(101, FileAssetService.CREATE),
                initialized.uploadId(), 1, new byte[FileAssetService.MULTIPART_PART_BYTES], null))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_MULTIPART_NOT_FOUND"));

        var first = java.util.Arrays.copyOf(content, FileAssetService.MULTIPART_PART_BYTES);
        service.uploadPart(owner, initialized.uploadId(), 1, first, sha256(first));
        assertThatThrownBy(() -> service.completeMultipart(owner, initialized.uploadId(),
                "a".repeat(64)))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_MULTIPART_HASH_MISMATCH"));
        var completed = service.completeMultipart(owner, initialized.uploadId(), digest);
        assertThat(completed.size()).isEqualTo(content.length);
        assertThat(completed.sha256()).isEqualTo(digest);
        assertThat(service.readContent(owner, completed.id())).isEqualTo(content);
        assertThat(service.completeMultipart(owner, initialized.uploadId(), digest).id())
                .isEqualTo(completed.id());
    }

    @Test
    void multipartAbortAndExpiryAreExplicitAndOversizeIsRejectedWithoutAllocation() {
        var bytes = new byte[]{4, 5, 6};
        var aborted = service.initializeMultipart(owner, "aborted.bin",
                "application/octet-stream", bytes.length, sha256(bytes));
        assertThat(service.abortMultipart(owner, aborted.uploadId()).status())
                .isEqualTo(MultipartUploadSession.State.ABORTED);
        assertThat(service.abortMultipart(owner, aborted.uploadId()).status())
                .isEqualTo(MultipartUploadSession.State.ABORTED);
        clock.advance(FileAssetService.MULTIPART_TERMINAL_RETENTION.plusSeconds(1));
        assertThatThrownBy(() -> service.abortMultipart(owner, aborted.uploadId()))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_MULTIPART_NOT_FOUND"));

        var expiring = service.initializeMultipart(owner, "expiring.bin",
                "application/octet-stream", bytes.length, sha256(bytes));
        clock.advance(FileAssetService.MULTIPART_TTL.plusSeconds(1));
        assertThatThrownBy(() -> service.uploadPart(owner, expiring.uploadId(), 1, bytes, null))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_MULTIPART_EXPIRED"));

        var exactMaximum = service.initializeMultipart(owner, "maximum.bin",
                "application/octet-stream", FileAssetService.MAX_MULTIPART_BYTES,
                "a".repeat(64));
        assertThat(exactMaximum.partCount()).isEqualTo(FileAssetService.MAX_MULTIPART_PARTS);
        service.abortMultipart(owner, exactMaximum.uploadId());

        assertThatThrownBy(() -> service.initializeMultipart(owner, "too-large.bin",
                "application/octet-stream", (long) FileAssetService.MAX_MULTIPART_BYTES + 1,
                "a".repeat(64)))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_MULTIPART_REQUEST_INVALID"));
    }

    @Test
    void multipartSessionLimitsAreBoundedAndExpiryReleasesCapacity() {
        var bytes = new byte[]{8};
        for (int index = 0; index < FileAssetService.MAX_ACTIVE_MULTIPART_PER_MEMBER; index++) {
            service.initializeMultipart(owner, "member-" + index + ".bin",
                    "application/octet-stream", 1, sha256(bytes));
        }
        assertThatThrownBy(() -> service.initializeMultipart(owner, "member-overflow.bin",
                "application/octet-stream", 1, sha256(bytes)))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_MULTIPART_MEMBER_LIMIT"));

        clock.advance(FileAssetService.MULTIPART_TTL.plusSeconds(1));
        assertThat(service.initializeMultipart(owner, "member-after-expiry.bin",
                "application/octet-stream", 1, sha256(bytes)).status())
                .isEqualTo(MultipartUploadSession.State.OPEN);
    }

    @Test
    void globalMultipartSessionLimitIsBoundedAndExpiredSessionsAreCleaned() {
        var bytes = new byte[]{9};
        for (int index = 0; index < FileAssetService.MAX_MULTIPART_SESSIONS; index++) {
            service.initializeMultipart(actor(1_000 + index, FileAssetService.CREATE),
                    "global-" + index + ".bin", "application/octet-stream", 1, sha256(bytes));
        }
        assertThatThrownBy(() -> service.initializeMultipart(
                actor(9_999, FileAssetService.CREATE), "global-overflow.bin",
                "application/octet-stream", 1, sha256(bytes)))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_MULTIPART_GLOBAL_LIMIT"));

        clock.advance(FileAssetService.MULTIPART_TTL.plusSeconds(1));
        assertThat(service.initializeMultipart(actor(9_999, FileAssetService.CREATE),
                "global-after-expiry.bin", "application/octet-stream", 1, sha256(bytes)).status())
                .isEqualTo(MultipartUploadSession.State.OPEN);
    }

    @Test
    void storageStatusIsManagerOnlyAndContainsOnlyTheProviderProjection() {
        var status = service.storageStatus(owner);
        assertThat(status.mode()).isEqualTo(FileStorageStatus.Mode.S3);
        assertThat(status.location()).isEqualTo("files-production");
        assertThat(status.available()).isTrue();
        assertThatThrownBy(() -> service.storageStatus(actor(101, FileAssetService.READ)))
                .isInstanceOfSatisfying(FileDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_FORBIDDEN"));
    }

    private static FileActor actor(long memberId, String... permissions) {
        return new FileActor(10, 20, memberId, Set.of(permissions));
    }

    private static byte[] png(int width, int height) {
        return encoded("png", width, height);
    }

    private static byte[] encoded(String format, int width, int height) {
        try {
            var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, width, height);
            graphics.dispose();
            try (var output = new ByteArrayOutputStream()) {
                if (!ImageIO.write(image, format, output)) {
                    throw new IllegalStateException("Missing ImageIO writer: " + format);
                }
                image.flush();
                return output.toByteArray();
            }
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
