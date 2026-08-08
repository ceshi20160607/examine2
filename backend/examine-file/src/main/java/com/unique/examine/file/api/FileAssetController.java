package com.unique.examine.file.api;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.file.domain.FileActor;
import com.unique.examine.file.domain.FileAsset;
import com.unique.examine.file.domain.FileAssetPage;
import com.unique.examine.file.domain.FileDomainException;
import com.unique.examine.file.domain.FilePreview;
import com.unique.examine.file.domain.FileStorageStatus;
import com.unique.examine.file.domain.FileThumbnail;
import com.unique.examine.file.domain.MultipartUploadSession;
import com.unique.examine.file.service.FileAssetService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/files")
public class FileAssetController {
    private final FileAssetService service;

    public FileAssetController(FileAssetService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<FilePageView> list(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String mediaType,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var result = call(() -> service.list(actor(sessionValue, systemId), page, size,
                keyword, mediaType));
        return ok(page(result), request);
    }

    @GetMapping("/storage-status")
    public ApiResponse<StorageStatusView> storageStatus(
            @PathVariable long systemId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var result = call(() -> service.storageStatus(actor(sessionValue, systemId)));
        return ok(new StorageStatusView(result.mode().name(), result.location(),
                result.maxSingleUploadBytes(), result.maxMultipartUploadBytes(),
                result.maxPartBytes(), result.available()), request);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileView>> upload(
            @PathVariable long systemId,
            @RequestPart("file") MultipartFile file,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var actor = actor(sessionValue, systemId);
        final byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(
                    "FILE_UPLOAD_READ_FAILED", "Uploaded content could not be read",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        var mediaType = file.getContentType() == null || file.getContentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType();
        var result = call(() -> service.register(actor, file.getOriginalFilename(), mediaType, content));
        return ResponseEntity.status(HttpStatus.CREATED).body(ok(view(result), request));
    }

    @GetMapping("/{fileId}")
    public ApiResponse<FileView> get(
            @PathVariable long systemId,
            @PathVariable long fileId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        return ok(view(call(() -> service.get(actor(sessionValue, systemId), fileId))), request);
    }

    @GetMapping("/{fileId}/content")
    public ResponseEntity<byte[]> download(
            @PathVariable long systemId,
            @PathVariable long fileId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue
    ) {
        var actor = actor(sessionValue, systemId);
        var asset = call(() -> service.get(actor, fileId));
        var content = call(() -> service.readContent(actor, fileId));
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(asset.mediaType());
        } catch (IllegalArgumentException ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(asset.originalName(), StandardCharsets.UTF_8).build().toString())
                .body(content);
    }

    @GetMapping("/{fileId}/preview")
    public ResponseEntity<byte[]> preview(
            @PathVariable long systemId,
            @PathVariable long fileId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue
    ) {
        var preview = call(() -> service.preview(actor(sessionValue, systemId), fileId));
        return inline(preview);
    }

    @GetMapping("/{fileId}/thumbnail")
    public ResponseEntity<byte[]> thumbnail(
            @PathVariable long systemId,
            @PathVariable long fileId,
            @RequestParam(defaultValue = "320") int maxWidth,
            @RequestParam(defaultValue = "240") int maxHeight,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue
    ) {
        var thumbnail = call(() -> service.thumbnail(
                actor(sessionValue, systemId), fileId, maxWidth, maxHeight));
        var content = thumbnail.content();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(thumbnail.originalName() + ".thumbnail.png", StandardCharsets.UTF_8)
                        .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300, no-transform")
                .body(content);
    }

    @PostMapping("/multipart-uploads")
    public ResponseEntity<ApiResponse<MultipartUploadView>> initializeMultipart(
            @PathVariable long systemId,
            @RequestBody MultipartInitializeRequest body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        if (body == null) throw invalidBody();
        var result = call(() -> service.initializeMultipart(actor(sessionValue, systemId),
                body.originalName(), body.mediaType(), body.sizeBytes(), body.sha256()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ok(multipart(result), request));
    }

    @PutMapping(value = "/multipart-uploads/{uploadId}/parts/{partNumber}",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ApiResponse<MultipartPartView> uploadPart(
            @PathVariable long systemId,
            @PathVariable String uploadId,
            @PathVariable int partNumber,
            @RequestHeader(value = "X-Part-SHA256", required = false) String partSha256,
            @RequestBody byte[] content,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var result = call(() -> service.uploadPart(actor(sessionValue, systemId), uploadId,
                partNumber, content, partSha256));
        return ok(new MultipartPartView(result.uploadId(), result.partNumber(), result.sizeBytes(),
                result.sha256(), result.replay(), result.uploadedPartCount(), result.partCount()), request);
    }

    @PostMapping("/multipart-uploads/{uploadId}:complete")
    public ApiResponse<FileView> completeMultipart(
            @PathVariable long systemId,
            @PathVariable String uploadId,
            @RequestBody MultipartCompleteRequest body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        if (body == null) throw invalidBody();
        var result = call(() -> service.completeMultipart(
                actor(sessionValue, systemId), uploadId, body.sha256()));
        return ok(view(result), request);
    }

    @DeleteMapping("/multipart-uploads/{uploadId}")
    public ApiResponse<MultipartAbortView> abortMultipart(
            @PathVariable long systemId,
            @PathVariable String uploadId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var result = call(() -> service.abortMultipart(actor(sessionValue, systemId), uploadId));
        return ok(new MultipartAbortView(result.uploadId(), result.status().name()), request);
    }

    @PostMapping("/{fileId}/references")
    public ApiResponse<FileView> addReference(
            @PathVariable long systemId,
            @PathVariable long fileId,
            @RequestBody ReferenceRequest body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var result = call(() -> service.addReference(
                actor(sessionValue, systemId), fileId, body.aggregateRef()));
        return ok(view(result), request);
    }

    @DeleteMapping("/{fileId}/references")
    public ApiResponse<FileView> removeReference(
            @PathVariable long systemId,
            @PathVariable long fileId,
            @RequestBody ReferenceRequest body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var result = call(() -> service.removeReference(
                actor(sessionValue, systemId), fileId, body.aggregateRef()));
        return ok(view(result), request);
    }

    @DeleteMapping("/{fileId}")
    public ApiResponse<FileView> delete(
            @PathVariable long systemId,
            @PathVariable long fileId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        return ok(view(call(() -> service.delete(actor(sessionValue, systemId), fileId))), request);
    }

    private static FileActor actor(Object value, long requestedSystemId) {
        if (!(value instanceof RequestSession session)
                || session.systemId() == null || session.tenantId() == null || session.memberId() == null) {
            throw new BusinessException(
                    "SYSTEM_CONTEXT_REQUIRED", "An authenticated system context is required",
                    HttpStatus.UNAUTHORIZED);
        }
        if (requestedSystemId <= 0 || session.systemId() != requestedSystemId) {
            throw new BusinessException(
                    "SYSTEM_CONTEXT_MISMATCH", "The requested system does not match the authenticated context",
                    HttpStatus.FORBIDDEN);
        }
        var mapped = new LinkedHashSet<String>();
        mapPermission(session.permissions(), mapped, "file.create", FileAssetService.CREATE);
        mapPermission(session.permissions(), mapped, "file.read", FileAssetService.READ);
        mapPermission(session.permissions(), mapped, "file.reference", FileAssetService.REFERENCE);
        mapPermission(session.permissions(), mapped, "file.manage", FileAssetService.MANAGE);
        return new FileActor(requestedSystemId, session.tenantId(), session.memberId(), Set.copyOf(mapped));
    }

    private static void mapPermission(Set<String> granted, Set<String> mapped, String api, String domain) {
        if (granted.contains(api) || granted.contains(domain)) {
            mapped.add(domain);
        }
    }

    private static <T> T call(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (FileDomainException exception) {
            var status = switch (exception.code()) {
                case "FILE_NOT_FOUND", "FILE_CONTENT_MISSING", "FILE_MULTIPART_NOT_FOUND" ->
                        HttpStatus.NOT_FOUND;
                case "FILE_FORBIDDEN" -> HttpStatus.FORBIDDEN;
                case "FILE_MULTIPART_EXPIRED" -> HttpStatus.GONE;
                case "FILE_STILL_REFERENCED", "FILE_CONCURRENT_MODIFICATION",
                     "FILE_VERSION_CONFLICT", "FILE_MULTIPART_PART_CONFLICT",
                     "FILE_MULTIPART_ABORTED", "FILE_MULTIPART_ALREADY_COMPLETED" ->
                        HttpStatus.CONFLICT;
                case "FILE_PREVIEW_UNSUPPORTED", "FILE_THUMBNAIL_UNSUPPORTED" ->
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE;
                default -> HttpStatus.UNPROCESSABLE_ENTITY;
            };
            throw new BusinessException(exception.code(), exception.getMessage(), status);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "FILE_REQUEST_INVALID", exception.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private static ResponseEntity<byte[]> inline(FilePreview preview) {
        var content = preview.content();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(preview.mediaType()))
                .contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(preview.originalName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300, no-transform")
                .header("Content-Security-Policy", "sandbox")
                .body(content);
    }

    private static FilePageView page(FileAssetPage page) {
        return new FilePageView(page.items().stream().map(FileAssetController::view).toList(),
                page.page(), page.size(), page.total(), page.totalPages());
    }

    private static MultipartUploadView multipart(MultipartUploadSession.Snapshot value) {
        return new MultipartUploadView(value.uploadId(), value.originalName(), value.mediaType(),
                value.sizeBytes(), value.sha256(), value.partSizeBytes(), value.partCount(),
                value.expiresAt().toString(), value.status().name());
    }

    private static BusinessException invalidBody() {
        return new BusinessException("FILE_REQUEST_INVALID", "Request body is required",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static FileView view(FileAsset asset) {
        var references = asset.references().values().stream()
                .map(reference -> new ReferenceView(
                        reference.target().type(), reference.target().id(),
                        Long.toString(reference.createdByMemberId()), reference.createdAt().toString()))
                .toList();
        return new FileView(
                Long.toString(asset.id()), asset.originalName(), asset.mediaType(), asset.size(), asset.sha256(),
                Long.toString(asset.uploaderMemberId()), asset.createdAt().toString(), asset.version(), references);
    }

    private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
        return ApiResponse.success(data, attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }

    public record ReferenceRequest(String targetType, String targetId) {
        AggregateRef aggregateRef() {
            return new AggregateRef(targetType, targetId);
        }
    }

    public record ReferenceView(
            String targetType,
            String targetId,
            String createdByMemberId,
            String createdAt
    ) { }

    public record FileView(
            String id,
            String originalName,
            String mediaType,
            long size,
            String sha256,
            String uploaderMemberId,
            String createdAt,
            long version,
            List<ReferenceView> references
    ) { }

    public record FilePageView(
            List<FileView> items,
            int page,
            int size,
            long total,
            long totalPages
    ) { }

    public record StorageStatusView(
            String mode,
            String location,
            long maxSingleUploadBytes,
            long maxMultipartUploadBytes,
            long maxPartBytes,
            boolean available
    ) { }

    public record MultipartInitializeRequest(
            String originalName,
            String mediaType,
            long sizeBytes,
            String sha256
    ) { }

    public record MultipartUploadView(
            String uploadId,
            String originalName,
            String mediaType,
            long sizeBytes,
            String sha256,
            int partSizeBytes,
            int partCount,
            String expiresAt,
            String status
    ) { }

    public record MultipartPartView(
            String uploadId,
            int partNumber,
            int sizeBytes,
            String sha256,
            boolean replay,
            int uploadedPartCount,
            int partCount
    ) { }

    public record MultipartCompleteRequest(String sha256) { }

    public record MultipartAbortView(String uploadId, String status) { }
}
