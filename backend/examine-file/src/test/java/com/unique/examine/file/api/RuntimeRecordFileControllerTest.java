package com.unique.examine.file.api;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import com.unique.examine.file.adapter.memory.InMemoryFileAssetRepository;
import com.unique.examine.file.adapter.memory.InMemoryFileContentStore;
import com.unique.examine.file.service.FileAssetService;
import com.unique.examine.file.service.RuntimeRecordFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
import java.util.Set;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RuntimeRecordFileControllerTest {
    private static final String BASE =
            "/api/v1/systems/10/runtime/modules/work_order/records/42/files";
    private static final Set<String> FULL = Set.of(
            "system.runtime.access",
            "module.work_order.view",
            "file.create",
            "file.read",
            "file.reference");

    private InMemoryFileAssetRepository repository;
    private InMemoryFileContentStore contentStore;
    private List<RuntimeRecordAccessFacade.RuntimeRecordAccessRequest> accessRequests;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository = new InMemoryFileAssetRepository();
        contentStore = new InMemoryFileContentStore();
        accessRequests = new ArrayList<>();
        mvc = mvc(request -> {
            accessRequests.add(request);
            return new RuntimeRecordAccessFacade.RuntimeRecordAccess(
                    Long.toString(request.recordId()),
                    7,
                    true);
        });
    }

    @Test
    void multipartListDownloadAndDetachExposeTheFrozenDtoAndScopedDownloadUrl()
            throws Exception {
        upload("one.txt", "one", "1");
        upload("two.txt", "two", "2");

        mvc.perform(authenticated(get(BASE + "?page=1&size=20"), session(100, FULL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].fileId").value("2"))
                .andExpect(jsonPath("$.data.items[0].originalName").value("two.txt"))
                .andExpect(jsonPath("$.data.items[0].mediaType").value("text/plain"))
                .andExpect(jsonPath("$.data.items[0].sizeBytes").value(3))
                .andExpect(jsonPath("$.data.items[0].sha256").isString())
                .andExpect(jsonPath("$.data.items[0].uploaderMemberId").value("100"))
                .andExpect(jsonPath("$.data.items[0].createdAt").isString())
                .andExpect(jsonPath("$.data.items[0].referencedByMemberId").value("100"))
                .andExpect(jsonPath("$.data.items[0].referencedAt").isString())
                .andExpect(jsonPath("$.data.items[0].downloadUrl").value(
                        BASE + "/2/content"))
                .andExpect(jsonPath("$.data.items[1].fileId").value("1"));

        mvc.perform(authenticated(get(BASE + "/1/content"), session(100, FULL)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes("one")))
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString("one.txt")));

        mvc.perform(authenticated(delete(BASE + "/1"), session(100, FULL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").value("1"))
                .andExpect(jsonPath("$.data.downloadUrl").value(BASE + "/1/content"));

        mvc.perform(authenticated(get(BASE), session(100, FULL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].fileId").value("2"));

        var detached = repository.findById(10, 20, 1).orElseThrow();
        assertThat(detached.references()).isEmpty();
        assertThat(contentStore.read(detached.objectKey()).orElseThrow())
                .isEqualTo(bytes("one"));
        assertThat(accessRequests).hasSize(6);
    }

    @Test
    void eachEndpointRevalidatesRecordViewAndFilePermissionAndHidesOtherRecords()
            throws Exception {
        upload("one.txt", "one", "1");
        var onlyView = Set.of("system.runtime.access", "module.work_order.view");

        mvc.perform(authenticated(get(BASE), session(101, onlyView)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_FORBIDDEN"));
        mvc.perform(authenticated(get(BASE + "/1/content"), session(101, onlyView)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_FORBIDDEN"));

        var otherRecord = BASE.replace("/records/42/", "/records/43/");
        mvc.perform(authenticated(get(otherRecord + "/1/content"), session(100, FULL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));

        var otherCreator = Set.of(
                "system.runtime.access",
                "module.work_order.view",
                "file.reference");
        mvc.perform(authenticated(delete(BASE + "/1"), session(101, otherCreator)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_FORBIDDEN"));

        assertThat(accessRequests).hasSize(5);
        assertThat(repository.findById(10, 20, 1).orElseThrow().referenceCount())
                .isEqualTo(1);
    }

    @Test
    void outOfScopeRecordAndContextMismatchFailBeforeCreatingAFile() throws Exception {
        var deniedMvc = mvc(request -> {
            throw new BusinessException(
                    "RECORD_NOT_FOUND",
                    "missing or outside VIEW scope",
                    HttpStatus.NOT_FOUND);
        });
        var upload = new MockMultipartFile(
                "file",
                "hidden.txt",
                "text/plain",
                bytes("hidden"));

        deniedMvc.perform(authenticated(multipart(BASE).file(upload), session(100, FULL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"));
        assertThat(repository.findById(10, 20, 1)).isEmpty();

        mvc.perform(authenticated(
                        get(BASE),
                        new TestSession(ContextType.PLATFORM, 10L, 20L, 100L, FULL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONTEXT_SYSTEM_MISMATCH"));
        mvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void pageBoundsAndUploadPermissionsAreRejectedWithoutPartialObjects()
            throws Exception {
        mvc.perform(authenticated(get(BASE + "?page=0&size=20"), session(100, FULL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECORD_FILE_PAGE_INVALID"));
        mvc.perform(authenticated(get(BASE + "?page=1&size=101"), session(100, FULL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECORD_FILE_SIZE_INVALID"));

        var missingReference = Set.of(
                "system.runtime.access",
                "module.work_order.view",
                "file.create");
        mvc.perform(authenticated(
                        multipart(BASE).file(new MockMultipartFile(
                                "file",
                                "denied.txt",
                                "text/plain",
                                bytes("denied"))),
                        session(100, missingReference)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_FORBIDDEN"));
        assertThat(repository.findById(10, 20, 1)).isEmpty();
    }

    @Test
    void bundleEndpointReturnsOrderedZipWithCanonicalAttachmentNameAndSupportsEmptyRecords()
            throws Exception {
        var emptyResponse = mvc.perform(authenticated(get(BASE + ":bundle"), session(100, FULL)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString("record-42-files.zip")))
                .andReturn()
                .getResponse();
        assertThat(unzip(emptyResponse.getContentAsByteArray())).isEmpty();

        upload("same.txt", "one", "1");
        upload("same.txt", "two", "2");
        var response = mvc.perform(authenticated(get(BASE + ":bundle"), session(100, FULL)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString("record-42-files.zip")))
                .andReturn()
                .getResponse();

        var entries = unzip(response.getContentAsByteArray());
        assertThat(entries.keySet()).containsExactly("same.txt", "same-1.txt");
        assertThat(entries.get("same.txt")).isEqualTo(bytes("two"));
        assertThat(entries.get("same-1.txt")).isEqualTo(bytes("one"));
    }

    @Test
    void bundleRequiresRecordViewAndFileReadAndIntegrityFailureReturnsNoZipBody()
            throws Exception {
        upload("one.txt", "one", "1");
        var onlyView = Set.of("system.runtime.access", "module.work_order.view");
        mvc.perform(authenticated(get(BASE + ":bundle"), session(101, onlyView)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_FORBIDDEN"))
                .andExpect(header().doesNotExist("Content-Disposition"));

        var asset = repository.findById(10, 20, 1).orElseThrow();
        contentStore.delete(asset.objectKey());
        contentStore.put(asset.objectKey(), bytes("two"));
        mvc.perform(authenticated(get(BASE + ":bundle"), session(100, FULL)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FILE_CONTENT_INTEGRITY_FAILED"))
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(content().contentTypeCompatibleWith("application/json"));

        var deniedMvc = mvc(request -> {
            throw new BusinessException(
                    "RECORD_NOT_FOUND",
                    "missing or outside VIEW scope",
                    HttpStatus.NOT_FOUND);
        });
        deniedMvc.perform(authenticated(get(BASE + ":bundle"), session(100, FULL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"))
                .andExpect(header().doesNotExist("Content-Disposition"));
    }

    private void upload(String name, String body, String expectedFileId) throws Exception {
        mvc.perform(authenticated(
                        multipart(BASE).file(new MockMultipartFile(
                                "file",
                                name,
                                "text/plain",
                                bytes(body))),
                        session(100, FULL)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileId").value(expectedFileId))
                .andExpect(jsonPath("$.data.downloadUrl").value(
                        BASE + "/" + expectedFileId + "/content"));
    }

    private MockMvc mvc(RuntimeRecordAccessFacade access) {
        var assets = new FileAssetService(
                repository,
                contentStore,
                Clock.fixed(
                        Instant.parse("2026-07-27T01:00:00Z"),
                        ZoneOffset.UTC));
        var service = new RuntimeRecordFileService(assets, repository, access);
        return MockMvcBuilders
                .standaloneSetup(new RuntimeRecordFileController(service))
                .setControllerAdvice(new TestBusinessExceptionHandler())
                .build();
    }

    private static MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder request,
            RequestSession session
    ) {
        return request
                .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session)
                .requestAttr(WebRequestAttributes.REQUEST_ID, "request-1")
                .requestAttr(WebRequestAttributes.TRACE_ID, "trace-1");
    }

    private static RequestSession session(long memberId, Set<String> permissions) {
        return new TestSession(ContextType.SYSTEM, 10L, 20L, memberId, permissions);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
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

    private record TestSession(
            ContextType contextType,
            Long systemId,
            Long tenantId,
            Long memberId,
            Set<String> permissions
    ) implements RequestSession {
        @Override
        public long sessionId() {
            return 1000;
        }

        @Override
        public long accountId() {
            return 2000;
        }

        @Override
        public long permissionVersion() {
            return 1;
        }
    }

    @RestControllerAdvice
    static final class TestBusinessExceptionHandler {
        @ExceptionHandler(BusinessException.class)
        ResponseEntity<ApiResponse<Void>> handle(BusinessException exception) {
            return ResponseEntity.status(exception.status()).body(ApiResponse.failure(
                    exception.code(),
                    exception.getMessage(),
                    "request-1",
                    "trace-1",
                    List.of()));
        }
    }
}
