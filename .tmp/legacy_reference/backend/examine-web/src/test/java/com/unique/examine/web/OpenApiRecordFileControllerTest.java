package com.unique.examine.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.file.openapi.OpenApiRecordFileFacade;
import com.unique.examine.openapi.domain.OpenApiApplication;
import com.unique.examine.openapi.domain.OpenApiCredential;
import com.unique.examine.openapi.security.OpenApiAuthentication;
import com.unique.examine.openapi.security.OpenApiMachineSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiRecordFileControllerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ROOT = "/openapi/v1/modules/orders/records/42/files";
    private static final String REQUEST_ID = "request-72";
    private static final String TRACE_ID = "trace-72";

    @Test
    void uploadsJsonFileAndForwardsBoundMachineContext() throws Exception {
        var operations = new CapturingOperations();
        operations.file = file();

        mvc(operations).perform(authenticated(post(ROOT)
                        .header("Idempotency-Key", "file-key-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalName":"evidence.txt","mediaType":"text/plain",
                                 "contentBase64":"c2lnbmVkLWZpbGU="}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.data.fileId").value("91"))
                .andExpect(jsonPath("$.data.downloadPath")
                        .value(ROOT + "/91/content"));

        assertThat(operations.uploadCalls).isOne();
        assertThat(operations.moduleCode).isEqualTo("orders");
        assertThat(operations.recordId).isEqualTo("42");
        assertThat(operations.idempotencyKey).isEqualTo("file-key-42");
        assertThat(operations.command.originalName()).isEqualTo("evidence.txt");
        assertThat(operations.command.contentBase64()).isEqualTo("c2lnbmVkLWZpbGU=");
        assertSession(operations.session);
    }

    @Test
    void listsMetadataAndDownloadsVerifiedBytes() throws Exception {
        var operations = new CapturingOperations();
        operations.file = file();
        operations.page = new OpenApiRecordFileFacade.PageView(
                List.of(file()), 2, 25, 26);
        operations.download = new OpenApiRecordFileFacade.DownloadView(
                "91", "evidence.txt", "text/plain", 11,
                "c63fe4b2131b6bf9691a04bd1d4238ca4e20f413e9aa16f93bedc6e7ff4230d3",
                "attachment; filename=\"evidence.txt\"", "signed-file".getBytes());
        var mvc = mvc(operations);

        mvc.perform(authenticated(get(ROOT + "?page=2&size=25")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(25))
                .andExpect(jsonPath("$.data.total").value(26))
                .andExpect(jsonPath("$.data.items[0].fileId").value("91"));

        mvc.perform(authenticated(get(ROOT + "/91/content")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().bytes("signed-file".getBytes()))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"evidence.txt\""))
                .andExpect(header().string(
                        OpenApiRecordFileController.CONTENT_SHA256_HEADER,
                        operations.download.sha256()));

        assertThat(operations.listCalls).isOne();
        assertThat(operations.downloadCalls).isOne();
        assertThat(operations.pageNumber).isEqualTo(2);
        assertThat(operations.pageSize).isEqualTo(25);
        assertThat(operations.fileId).isEqualTo("91");
        assertSession(operations.session);
    }

    @Test
    void rejectsMissingOrMismatchedMachineBinding() throws Exception {
        var operations = new CapturingOperations();
        var mvc = mvc(operations);

        mvc.perform(get(ROOT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OPENAPI_AUTH_REQUIRED"));

        var current = machine();
        var different = new OpenApiMachineSession(
                10, 700, 20L, 31L, 40L, 9,
                Set.of("system.runtime.access", "module.orders.view", "file.read"));
        mvc.perform(get(ROOT)
                        .requestAttr(OpenApiAuthentication.REQUEST_ATTRIBUTE,
                                authentication(current))
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, different))
                .andExpect(status().isUnauthorized());

        assertThat(operations.totalCalls()).isZero();
    }

    private static MockMvc mvc(CapturingOperations operations) {
        return MockMvcBuilders.standaloneSetup(new OpenApiRecordFileController(operations))
                .setControllerAdvice(new TestExceptionAdvice())
                .setMessageConverters(
                        new ByteArrayHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(JSON))
                .build();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            authenticated(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        var machine = machine();
        return request
                .requestAttr(OpenApiAuthentication.REQUEST_ATTRIBUTE, authentication(machine))
                .requestAttr(RequestSession.REQUEST_ATTRIBUTE, machine)
                .requestAttr(WebRequestAttributes.REQUEST_ID, REQUEST_ID)
                .requestAttr(WebRequestAttributes.TRACE_ID, TRACE_ID);
    }

    private static OpenApiAuthentication authentication(OpenApiMachineSession machine) {
        var now = Instant.parse("2026-08-04T00:00:00Z");
        var application = new OpenApiApplication(
                10, 20, 30, 40, "app-key", "File integration",
                OpenApiApplication.Status.ACTIVE,
                Set.of("file.write", "file.read"), List.of(), 100,
                3, now, 1, now, 1, 0);
        var credential = new OpenApiCredential(
                50, 10, 3, "secret-ref", OpenApiCredential.Status.ACTIVE,
                now, null, now, 1);
        return new OpenApiAuthentication(application, credential, machine);
    }

    private static OpenApiMachineSession machine() {
        return new OpenApiMachineSession(
                10, 700, 20L, 30L, 40L, 9,
                Set.of("system.runtime.access", "module.orders.view",
                        "file.create", "file.reference", "file.read"));
    }

    private static OpenApiRecordFileFacade.FileView file() {
        return new OpenApiRecordFileFacade.FileView(
                "91", "evidence.txt", "text/plain", 11,
                "c63fe4b2131b6bf9691a04bd1d4238ca4e20f413e9aa16f93bedc6e7ff4230d3",
                "40", "2026-08-04T00:00:00Z", "40", "2026-08-04T00:00:00Z",
                ROOT + "/91/content");
    }

    private static void assertSession(OpenApiRecordFileFacade.Session session) {
        assertThat(session.applicationId()).isEqualTo(10);
        assertThat(session.accountId()).isEqualTo(700);
        assertThat(session.systemId()).isEqualTo(20);
        assertThat(session.tenantId()).isEqualTo(30);
        assertThat(session.serviceMemberId()).isEqualTo(40);
        assertThat(session.permissions()).contains("system.runtime.access", "file.read");
        assertThat(session.requestId()).isEqualTo(REQUEST_ID);
        assertThat(session.traceId()).isEqualTo(TRACE_ID);
    }

    @RestControllerAdvice
    private static final class TestExceptionAdvice {
        @ExceptionHandler(BusinessException.class)
        ResponseEntity<Map<String, Object>> business(BusinessException error) {
            return ResponseEntity.status(error.status()).body(Map.of(
                    "code", error.code(), "message", error.getMessage()));
        }
    }

    private static final class CapturingOperations
            implements OpenApiRecordFileController.FileOperations {
        private OpenApiRecordFileFacade.FileView file;
        private OpenApiRecordFileFacade.PageView page;
        private OpenApiRecordFileFacade.DownloadView download;
        private OpenApiRecordFileFacade.Session session;
        private OpenApiRecordFileFacade.UploadCommand command;
        private String moduleCode;
        private String recordId;
        private String fileId;
        private String idempotencyKey;
        private int pageNumber;
        private int pageSize;
        private int uploadCalls;
        private int listCalls;
        private int downloadCalls;

        @Override
        public OpenApiRecordFileFacade.FileView upload(
                OpenApiRecordFileFacade.Session requestedSession,
                String requestedModuleCode,
                String requestedRecordId,
                OpenApiRecordFileFacade.UploadCommand requestedCommand,
                String requestedIdempotencyKey) {
            capture(requestedSession, requestedModuleCode, requestedRecordId);
            command = requestedCommand;
            idempotencyKey = requestedIdempotencyKey;
            uploadCalls++;
            return file;
        }

        @Override
        public OpenApiRecordFileFacade.PageView list(
                OpenApiRecordFileFacade.Session requestedSession,
                String requestedModuleCode,
                String requestedRecordId,
                int requestedPage,
                int requestedSize) {
            capture(requestedSession, requestedModuleCode, requestedRecordId);
            pageNumber = requestedPage;
            pageSize = requestedSize;
            listCalls++;
            return page;
        }

        @Override
        public OpenApiRecordFileFacade.DownloadView download(
                OpenApiRecordFileFacade.Session requestedSession,
                String requestedModuleCode,
                String requestedRecordId,
                String requestedFileId) {
            capture(requestedSession, requestedModuleCode, requestedRecordId);
            fileId = requestedFileId;
            downloadCalls++;
            return download;
        }

        private void capture(
                OpenApiRecordFileFacade.Session requestedSession,
                String requestedModuleCode,
                String requestedRecordId) {
            session = requestedSession;
            moduleCode = requestedModuleCode;
            recordId = requestedRecordId;
        }

        private int totalCalls() {
            return uploadCalls + listCalls + downloadCalls;
        }
    }
}
