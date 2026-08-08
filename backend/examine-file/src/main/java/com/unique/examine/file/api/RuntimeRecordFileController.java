package com.unique.examine.file.api;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.file.domain.FileAsset;
import com.unique.examine.file.domain.FileDomainException;
import com.unique.examine.file.domain.FileReference;
import com.unique.examine.file.domain.RuntimeRecordFile;
import com.unique.examine.file.domain.RuntimeRecordFileActor;
import com.unique.examine.file.domain.RuntimeRecordFilePage;
import com.unique.examine.file.service.RuntimeRecordFileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Supplier;

@RestController
@RequestMapping(
        "/api/v1/systems/{systemId}/runtime/modules/{moduleCode}"
                + "/records/{recordId}")
public class RuntimeRecordFileController {
    private final RuntimeRecordFileService service;

    public RuntimeRecordFileController(RuntimeRecordFileService service) {
        this.service = service;
    }

    @GetMapping("/files")
    public ApiResponse<PageView> page(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var actor = actor(sessionValue, systemId, moduleCode, recordId);
        return ok(PageView.from(call(() -> service.page(actor, page, size)), actor), request);
    }

    @PostMapping(path = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ItemView>> attach(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestPart("file") MultipartFile file,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var actor = actor(sessionValue, systemId, moduleCode, recordId);
        var content = content(file);
        var mediaType = file.getContentType() == null || file.getContentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : file.getContentType();
        var attached = call(() -> service.attach(
                actor,
                file.getOriginalFilename(),
                mediaType,
                content));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ok(ItemView.from(attached, actor), request));
    }

    @GetMapping("/files/{fileId}/content")
    public ResponseEntity<byte[]> download(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @PathVariable long fileId,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue
    ) {
        var result = call(() -> service.download(
                actor(sessionValue, systemId, moduleCode, recordId),
                fileId));
        var asset = result.file().asset();
        return ResponseEntity.ok()
                .contentType(mediaType(asset.mediaType()))
                .contentLength(result.content().length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(asset.originalName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(result.content());
    }

    @GetMapping("/files:bundle")
    public ResponseEntity<byte[]> bundle(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue
    ) {
        var result = call(() -> service.bundle(
                actor(sessionValue, systemId, moduleCode, recordId)));
        var content = result.content();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(content.length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(
                                        "record-" + result.recordId() + "-files.zip",
                                        StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(content);
    }

    @DeleteMapping("/files/{fileId}")
    public ApiResponse<ItemView> detach(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @PathVariable long fileId,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var actor = actor(sessionValue, systemId, moduleCode, recordId);
        return ok(
                ItemView.from(call(() -> service.detach(actor, fileId)), actor),
                request);
    }

    private static RuntimeRecordFileActor actor(
            Object value,
            long requestedSystemId,
            String moduleCode,
            long recordId
    ) {
        if (!(value instanceof RequestSession session)) {
            throw new BusinessException(
                    "AUTH_REQUIRED",
                    "Authentication is required",
                    HttpStatus.UNAUTHORIZED);
        }
        if (session.contextType() != ContextType.SYSTEM
                || session.systemId() == null
                || session.systemId() != requestedSystemId) {
            throw new BusinessException(
                    "CONTEXT_SYSTEM_MISMATCH",
                    "The authenticated system context does not match the request",
                    HttpStatus.FORBIDDEN);
        }
        if (session.tenantId() == null) {
            throw new BusinessException(
                    "CONTEXT_TENANT_REQUIRED",
                    "The authenticated tenant context is required",
                    HttpStatus.FORBIDDEN);
        }
        if (session.memberId() == null) {
            throw new BusinessException(
                    "CONTEXT_MEMBER_REQUIRED",
                    "The authenticated member context is required",
                    HttpStatus.FORBIDDEN);
        }
        if (moduleCode == null || !moduleCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new BusinessException(
                    "RECORD_FILE_MODULE_CODE_INVALID",
                    "moduleCode is invalid",
                    HttpStatus.BAD_REQUEST);
        }
        if (recordId <= 0) {
            throw new BusinessException(
                    "RECORD_FILE_RECORD_ID_INVALID",
                    "recordId must be positive",
                    HttpStatus.BAD_REQUEST);
        }
        return new RuntimeRecordFileActor(
                requestedSystemId,
                session.tenantId(),
                session.memberId(),
                session.permissions() == null ? Set.of() : Set.copyOf(session.permissions()),
                moduleCode,
                recordId);
    }

    private static byte[] content(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(
                    "FILE_UPLOAD_READ_FAILED",
                    "Uploaded content could not be read",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private static MediaType mediaType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (IllegalArgumentException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static <T> T call(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (FileDomainException exception) {
            var status = switch (exception.code()) {
                case "FILE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
                case "FILE_FORBIDDEN" -> HttpStatus.FORBIDDEN;
                case "RECORD_FILE_PAGE_INVALID", "RECORD_FILE_SIZE_INVALID" ->
                        HttpStatus.BAD_REQUEST;
                case "FILE_STILL_REFERENCED", "FILE_CONCURRENT_MODIFICATION",
                        "FILE_VERSION_CONFLICT" -> HttpStatus.CONFLICT;
                default -> HttpStatus.UNPROCESSABLE_ENTITY;
            };
            throw new BusinessException(exception.code(), exception.getMessage(), status);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "FILE_REQUEST_INVALID",
                    exception.getMessage(),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
        return ApiResponse.success(
                data,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }

    public record PageView(
            java.util.List<ItemView> items,
            int page,
            int size,
            long total
    ) {
        static PageView from(
                RuntimeRecordFilePage value,
                RuntimeRecordFileActor actor
        ) {
            return new PageView(
                    value.items().stream().map(item -> ItemView.from(item, actor)).toList(),
                    value.page(),
                    value.size(),
                    value.total());
        }
    }

    public record ItemView(
            String fileId,
            String originalName,
            String mediaType,
            long sizeBytes,
            String sha256,
            String uploaderMemberId,
            String createdAt,
            String referencedByMemberId,
            String referencedAt,
            String downloadUrl
    ) {
        static ItemView from(
                RuntimeRecordFile value,
                RuntimeRecordFileActor actor
        ) {
            FileAsset asset = value.asset();
            FileReference reference = value.reference();
            return new ItemView(
                    Long.toString(asset.id()),
                    asset.originalName(),
                    asset.mediaType(),
                    asset.size(),
                    asset.sha256(),
                    Long.toString(asset.uploaderMemberId()),
                    asset.createdAt().toString(),
                    Long.toString(reference.createdByMemberId()),
                    reference.createdAt().toString(),
                    basePath(actor, reference.target().id()) + "/" + asset.id() + "/content");
        }

        private static String basePath(RuntimeRecordFileActor actor, String canonicalRecordId) {
            return "/api/v1/systems/" + actor.systemId()
                    + "/runtime/modules/" + actor.moduleCode()
                    + "/records/" + canonicalRecordId
                    + "/files";
        }
    }
}
