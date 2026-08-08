package com.unique.examine.file.api;

import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.file.adapter.memory.InMemoryFileAssetRepository;
import com.unique.examine.file.adapter.memory.InMemoryFileContentStore;
import com.unique.examine.file.service.FileAssetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileAssetControllerTest {
    private FileAssetController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        var service = new FileAssetService(
                new InMemoryFileAssetRepository(),
                new InMemoryFileContentStore(),
                Clock.fixed(Instant.parse("2026-07-25T10:00:00Z"), ZoneOffset.UTC));
        controller = new FileAssetController(service);
        request = new MockHttpServletRequest();
        request.setAttribute(WebRequestAttributes.REQUEST_ID, "request-1");
        request.setAttribute(WebRequestAttributes.TRACE_ID, "trace-1");
    }

    @Test
    void authenticatedHttpLifecycleUploadsDownloadsReferencesAndDeletes() {
        var owner = session(100, "file.create", "file.read", "file.reference");
        var upload = new MockMultipartFile(
                "file", "contract.txt", "text/plain",
                "contract".getBytes(StandardCharsets.UTF_8));

        var uploaded = controller.upload(10, upload, owner, request);
        assertThat(uploaded.getStatusCode().value()).isEqualTo(201);
        assertThat(uploaded.getBody()).isNotNull();
        assertThat(uploaded.getBody().requestId()).isEqualTo("request-1");
        assertThat(uploaded.getBody().data().originalName()).isEqualTo("contract.txt");
        assertThat(uploaded.getBody().data().sha256()).hasSize(64);

        var fileId = Long.parseLong(uploaded.getBody().data().id());
        var download = controller.download(10, fileId, owner);
        assertThat(download.getBody()).isEqualTo("contract".getBytes(StandardCharsets.UTF_8));
        assertThat(download.getHeaders().getContentDisposition().getFilename()).isEqualTo("contract.txt");

        var target = new FileAssetController.ReferenceRequest("MODULE_RECORD", "42");
        var referenced = controller.addReference(10, fileId, target, owner, request);
        assertThat(referenced.data().references()).hasSize(1);
        assertThat(referenced.data().references().getFirst().targetId()).isEqualTo("42");

        assertThatThrownBy(() -> controller.delete(10, fileId, owner, request))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.code()).isEqualTo("FILE_STILL_REFERENCED");
                    assertThat(error.status().value()).isEqualTo(409);
                });

        controller.removeReference(10, fileId, target, owner, request);
        assertThat(controller.delete(10, fileId, owner, request).data().id())
                .isEqualTo(Long.toString(fileId));
        assertThatThrownBy(() -> controller.get(10, fileId, owner, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_NOT_FOUND"));
    }

    @Test
    void pathCannotOverrideTrustedSessionScope() {
        var session = session(100, "file.create");
        var upload = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> controller.upload(11, upload, session, request))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.code()).isEqualTo("SYSTEM_CONTEXT_MISMATCH");
                    assertThat(error.status().value()).isEqualTo(403);
                });
    }

    @Test
    void anotherMemberNeedsExplicitReadPermission() {
        var upload = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);
        var uploaded = controller.upload(10, upload, session(100, "file.create"), request);
        var fileId = Long.parseLong(uploaded.getBody().data().id());

        assertThatThrownBy(() -> controller.get(10, fileId, session(101), request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_FORBIDDEN"));

        assertThat(controller.get(10, fileId, session(101, "file.read"), request).data().id())
                .isEqualTo(Long.toString(fileId));
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
        @Override
        public ContextType contextType() {
            return ContextType.SYSTEM;
        }

        @Override
        public long permissionVersion() {
            return 1;
        }
    }
}
