package com.unique.examine.file.api;

import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.file.adapter.memory.InMemoryFileContentStore;
import com.unique.examine.file.domain.FileStorageStatus;
import com.unique.examine.file.service.FileAssetService;
import com.unique.examine.file.support.TestFileAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileAssetAdvancedControllerTest {
    private FileAssetController controller;
    private MockHttpServletRequest request;
    private RequestSession owner;

    @BeforeEach
    void setUp() {
        var service = new FileAssetService(new TestFileAssetRepository(),
                new InMemoryFileContentStore(),
                () -> new FileStorageStatus(FileStorageStatus.Mode.LOCAL, "Managed local storage",
                        FileAssetService.MAX_CONTENT_BYTES, FileAssetService.MAX_MULTIPART_BYTES,
                        FileAssetService.MULTIPART_PART_BYTES, true),
                Clock.fixed(Instant.parse("2026-08-06T06:00:00Z"), ZoneOffset.UTC));
        controller = new FileAssetController(service);
        request = new MockHttpServletRequest();
        request.setAttribute(WebRequestAttributes.REQUEST_ID, "request-file-110");
        request.setAttribute(WebRequestAttributes.TRACE_ID, "trace-file-110");
        owner = session(100, "file.create", "file.read", "file.manage");
    }

    @Test
    void exposesPagedListSanitizedStatusSafePreviewAndThumbnailHeaders() throws Exception {
        var image = png(4, 2);
        var uploaded = controller.upload(10,
                new MockMultipartFile("file", "photo.png", "image/png", image),
                owner, request).getBody().data();
        var listed = controller.list(10, 1, 20, "photo", "image/*", owner, request).data();
        assertThat(listed.items()).extracting(FileAssetController.FileView::id)
                .containsExactly(uploaded.id());
        assertThat(listed.total()).isOne();
        assertThat(listed.totalPages()).isOne();

        var status = controller.storageStatus(10, owner, request).data();
        assertThat(status.mode()).isEqualTo("LOCAL");
        assertThat(status.location()).isEqualTo("Managed local storage");

        var preview = controller.preview(10, Long.parseLong(uploaded.id()), owner);
        assertThat(preview.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(preview.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("private");
        assertThat(preview.getHeaders().getFirst("Content-Security-Policy")).isEqualTo("sandbox");
        assertThat(preview.getHeaders().getContentDisposition().getType()).isEqualTo("inline");

        var thumbnail = controller.thumbnail(10, Long.parseLong(uploaded.id()), 2, 2, owner);
        assertThat(thumbnail.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(thumbnail.getBody())).getWidth())
                .isEqualTo(2);

        var text = controller.upload(10,
                new MockMultipartFile("file", "notes.txt", "text/plain", "notes".getBytes()),
                owner, request).getBody().data();
        assertThatThrownBy(() -> controller.preview(10, Long.parseLong(text.id()), owner))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.code()).isEqualTo("FILE_PREVIEW_UNSUPPORTED");
                    assertThat(error.status().value()).isEqualTo(415);
                });
    }

    @Test
    void exposesTheFrozenMultipartRequestAndResponseShapeWithIdempotentPartAndComplete() {
        var content = new byte[]{1, 2, 3};
        var digest = sha256(content);
        var initialized = controller.initializeMultipart(10,
                new FileAssetController.MultipartInitializeRequest(
                        "large.bin", "application/octet-stream", content.length, digest),
                owner, request);
        assertThat(initialized.getStatusCode().value()).isEqualTo(201);
        var session = initialized.getBody().data();
        assertThat(session.status()).isEqualTo("OPEN");
        assertThat(session.partSizeBytes()).isEqualTo(5 * 1024 * 1024);
        assertThat(session.partCount()).isOne();

        var part = controller.uploadPart(10, session.uploadId(), 1, digest, content, owner, request).data();
        assertThat(part.sha256()).isEqualTo(digest);
        assertThat(part.replay()).isFalse();
        assertThat(controller.uploadPart(10, session.uploadId(), 1, null, content,
                owner, request).data().replay()).isTrue();

        var completed = controller.completeMultipart(10, session.uploadId(),
                new FileAssetController.MultipartCompleteRequest(digest), owner, request).data();
        assertThat(completed.size()).isEqualTo(content.length);
        assertThat(controller.completeMultipart(10, session.uploadId(),
                new FileAssetController.MultipartCompleteRequest(digest), owner, request).data().id())
                .isEqualTo(completed.id());

        var abortable = controller.initializeMultipart(10,
                new FileAssetController.MultipartInitializeRequest(
                        "cancel.bin", "application/octet-stream", content.length, digest),
                owner, request).getBody().data();
        assertThat(controller.abortMultipart(10, abortable.uploadId(), owner, request).data().status())
                .isEqualTo("ABORTED");
    }

    private static byte[] png(int width, int height) {
        try {
            var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            try (var output = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", output);
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

    private static RequestSession session(long memberId, String... permissions) {
        return new TestSession(1, 2, 10L, 20L, memberId, Set.of(permissions));
    }

    private record TestSession(
            long sessionId,
            long accountId,
            Long systemId,
            Long tenantId,
            Long memberId,
            Set<String> permissions
    ) implements RequestSession {
        @Override public ContextType contextType() { return ContextType.SYSTEM; }
        @Override public long permissionVersion() { return 1; }
    }
}
