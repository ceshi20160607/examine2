package com.unique.examine.web;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.file.openapi.OpenApiRecordFileFacade;
import com.unique.examine.openapi.security.OpenApiAuthentication;
import com.unique.examine.openapi.security.OpenApiMachineSession;
import com.unique.examine.openapi.security.OpenApiSecurityErrors;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/openapi/v1/modules/{moduleCode}/records/{recordId}/files")
public class OpenApiRecordFileController {
    public static final String CONTENT_SHA256_HEADER = "X-Content-SHA256";

    private final FileOperations files;

    @Autowired
    public OpenApiRecordFileController(OpenApiRecordFileFacade files) {
        this(new FacadeFileOperations(files));
    }

    OpenApiRecordFileController(FileOperations files) {
        this.files = Objects.requireNonNull(files, "files");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OpenApiRecordFileFacade.FileView>> upload(
            @PathVariable String moduleCode,
            @PathVariable String recordId,
            @RequestBody OpenApiRecordFileFacade.UploadCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = OpenApiAuthentication.REQUEST_ATTRIBUTE, required = false)
            Object authenticationValue,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        var uploaded = files.upload(
                session(authenticationValue, sessionValue, request),
                moduleCode,
                recordId,
                body,
                idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(success(uploaded, request));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<OpenApiRecordFileFacade.PageView>> list(
            @PathVariable String moduleCode,
            @PathVariable String recordId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = OpenApiAuthentication.REQUEST_ATTRIBUTE, required = false)
            Object authenticationValue,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        var result = files.list(
                session(authenticationValue, sessionValue, request),
                moduleCode,
                recordId,
                page,
                size);
        return ResponseEntity.ok(success(result, request));
    }

    @GetMapping("/{fileId}/content")
    public ResponseEntity<byte[]> download(
            @PathVariable String moduleCode,
            @PathVariable String recordId,
            @PathVariable String fileId,
            @RequestAttribute(value = OpenApiAuthentication.REQUEST_ATTRIBUTE, required = false)
            Object authenticationValue,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        var result = files.download(
                session(authenticationValue, sessionValue, request),
                moduleCode,
                recordId,
                fileId);
        return ResponseEntity.ok()
                .contentType(mediaType(result.mediaType()))
                .contentLength(result.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, result.contentDisposition())
                .header(CONTENT_SHA256_HEADER, result.sha256())
                .body(result.content());
    }

    private static OpenApiRecordFileFacade.Session session(
            Object authenticationValue,
            Object sessionValue,
            HttpServletRequest request
    ) {
        if (!(authenticationValue instanceof OpenApiAuthentication authentication)
                || !(sessionValue instanceof OpenApiMachineSession machineSession)
                || !machineSession.equals(authentication.session())
                || !Objects.equals(machineSession.systemId(), authentication.application().systemId())
                || !Objects.equals(machineSession.tenantId(), authentication.application().tenantId())
                || !Objects.equals(
                        machineSession.memberId(), authentication.application().serviceMemberId())) {
            throw OpenApiSecurityErrors.authenticationRequired();
        }
        return new OpenApiRecordFileFacade.Session(
                authentication.application().id(),
                machineSession.accountId(),
                machineSession.systemId(),
                machineSession.tenantId(),
                machineSession.memberId(),
                machineSession.permissions(),
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static MediaType mediaType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (IllegalArgumentException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static <T> ApiResponse<T> success(T value, HttpServletRequest request) {
        return ApiResponse.success(
                value,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }

    interface FileOperations {
        OpenApiRecordFileFacade.FileView upload(
                OpenApiRecordFileFacade.Session session,
                String moduleCode,
                String recordId,
                OpenApiRecordFileFacade.UploadCommand command,
                String idempotencyKey);

        OpenApiRecordFileFacade.PageView list(
                OpenApiRecordFileFacade.Session session,
                String moduleCode,
                String recordId,
                int page,
                int size);

        OpenApiRecordFileFacade.DownloadView download(
                OpenApiRecordFileFacade.Session session,
                String moduleCode,
                String recordId,
                String fileId);
    }

    private record FacadeFileOperations(OpenApiRecordFileFacade delegate)
            implements FileOperations {
        private FacadeFileOperations {
            Objects.requireNonNull(delegate, "files");
        }

        @Override
        public OpenApiRecordFileFacade.FileView upload(
                OpenApiRecordFileFacade.Session session,
                String moduleCode,
                String recordId,
                OpenApiRecordFileFacade.UploadCommand command,
                String idempotencyKey) {
            return delegate.upload(session, moduleCode, recordId, command, idempotencyKey);
        }

        @Override
        public OpenApiRecordFileFacade.PageView list(
                OpenApiRecordFileFacade.Session session,
                String moduleCode,
                String recordId,
                int page,
                int size) {
            return delegate.list(session, moduleCode, recordId, page, size);
        }

        @Override
        public OpenApiRecordFileFacade.DownloadView download(
                OpenApiRecordFileFacade.Session session,
                String moduleCode,
                String recordId,
                String fileId) {
            return delegate.download(session, moduleCode, recordId, fileId);
        }
    }
}
