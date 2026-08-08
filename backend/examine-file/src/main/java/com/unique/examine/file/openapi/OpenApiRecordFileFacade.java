package com.unique.examine.file.openapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.OutboxEvent;
import com.unique.examine.core.api.OutboxFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.file.domain.FileDomainException;
import com.unique.examine.file.domain.RuntimeRecordFile;
import com.unique.examine.file.domain.RuntimeRecordFileActor;
import com.unique.examine.file.service.RuntimeRecordFileService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Public file-owner boundary used after OpenAPI authentication and scope checks.
 * It exposes no storage object key and delegates record, tenant, reference and
 * file permissions to {@link RuntimeRecordFileService}.
 */
public class OpenApiRecordFileFacade {
    public static final int MAX_DECODED_BYTES = 512 * 1024;
    private static final int MAX_CANONICAL_BASE64_CHARS = 4 * ((MAX_DECODED_BYTES + 2) / 3);
    private static final String IDEMPOTENCY_SCOPE = "OPENAPI_RECORD_FILE";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final RuntimeRecordFileService files;
    private final IdempotencyFacade idempotency;
    private final OperationAuditFacade audits;
    private final OutboxFacade outbox;
    private final ObjectMapper objectMapper;

    public OpenApiRecordFileFacade(
            RuntimeRecordFileService files,
            IdempotencyFacade idempotency,
            OperationAuditFacade audits,
            OutboxFacade outbox,
            ObjectMapper objectMapper
    ) {
        this.files = Objects.requireNonNull(files, "files");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Transactional
    public FileView upload(
            Session session,
            String moduleCode,
            String recordId,
            UploadCommand command,
            String idempotencyKey
    ) {
        Objects.requireNonNull(session, "session");
        if (command == null) {
            throw invalid("OPENAPI_FILE_REQUEST_INVALID", "File upload body is required");
        }
        var actor = actor(session, moduleCode, recordId);
        call(() -> {
            files.requireAttachAccess(actor);
            return null;
        });
        requireIdempotencyKey(idempotencyKey);
        var content = decode(command.contentBase64());
        var identity = new UploadIdentity(
                command.originalName(), command.mediaType(), content.length, sha256(content));
        var requestHash = sha256(write(identity).getBytes(StandardCharsets.UTF_8));
        var scopeKey = uploadScope(session, moduleCode, recordId);
        var existing = idempotency.find(IDEMPOTENCY_SCOPE, scopeKey, idempotencyKey);
        if (existing.isPresent()) {
            var stored = existing.orElseThrow();
            if (!requestHash.equals(stored.requestHash())) {
                throw conflict("IDEMPOTENCY_CONFLICT",
                        "The same Idempotency-Key cannot be used for different file metadata or bytes");
            }
            if (!"COMPLETED".equals(stored.status()) || stored.responseBody() == null) {
                throw conflict("REQUEST_IN_PROGRESS", "The file upload is already being processed");
            }
            return read(stored.responseBody(), FileView.class);
        }

        final long idempotencyId;
        try {
            idempotencyId = idempotency.begin(
                    IDEMPOTENCY_SCOPE, scopeKey, idempotencyKey, requestHash, IDEMPOTENCY_TTL);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("REQUEST_IN_PROGRESS", "The file upload is already being processed");
        }
        var attached = call(() -> files.attach(
                actor, command.originalName(), command.mediaType(), content));
        var response = view(moduleCode, attached);
        changed(session, moduleCode, attached, response);
        idempotency.complete(idempotencyId, 201, "OK", write(response));
        return response;
    }

    @Transactional(readOnly = true)
    public PageView list(
            Session session,
            String moduleCode,
            String recordId,
            int page,
            int size
    ) {
        Objects.requireNonNull(session, "session");
        var result = call(() -> files.page(actor(session, moduleCode, recordId), page, size));
        return new PageView(
                result.items().stream().map(value -> view(moduleCode, value)).toList(),
                result.page(), result.size(), result.total());
    }

    @Transactional(readOnly = true)
    public DownloadView download(
            Session session,
            String moduleCode,
            String recordId,
            String fileId
    ) {
        Objects.requireNonNull(session, "session");
        var result = call(() -> files.download(
                actor(session, moduleCode, recordId), positiveId(fileId, "FILE_NOT_FOUND")));
        var asset = result.file().asset();
        return new DownloadView(
                Long.toString(asset.id()),
                asset.originalName(),
                safeMediaType(asset.mediaType()),
                asset.size(),
                asset.sha256(),
                ContentDisposition.attachment()
                        .filename(asset.originalName(), StandardCharsets.UTF_8)
                        .build()
                        .toString(),
                result.content());
    }

    private void changed(
            Session session,
            String moduleCode,
            RuntimeRecordFile attached,
            FileView response
    ) {
        var asset = attached.asset();
        var aggregate = new AggregateRef("FILE_ASSET", Long.toString(asset.id()));
        audits.recordSuccess(OperationAudit.success(
                new OperationAudit.Actor(session.accountId(), "OPENAPI"),
                new OperationAudit.Context(ContextType.SYSTEM, session.systemId(), session.tenantId()),
                aggregate,
                "RUNTIME_RECORD_FILE_ATTACHED",
                null,
                response,
                session.requestId(),
                session.traceId()));
        outbox.enqueue(new OutboxEvent(
                "RUNTIME_RECORD_FILE_ATTACHED",
                1,
                aggregate,
                new OutboxEvent.Context(session.systemId(), session.tenantId()),
                "runtime-record-file:" + session.systemId() + ":" + session.tenantId() + ":" + asset.id(),
                Map.of(
                        "systemId", Long.toString(session.systemId()),
                        "tenantId", Long.toString(session.tenantId()),
                        "moduleCode", moduleCode,
                        "recordId", attached.reference().target().id(),
                        "fileId", Long.toString(asset.id()),
                        "sizeBytes", Long.toString(asset.size()),
                        "sha256", asset.sha256(),
                        "uploaderMemberId", Long.toString(asset.uploaderMemberId())),
                session.traceId()));
    }

    private static FileView view(String moduleCode, RuntimeRecordFile value) {
        var asset = value.asset();
        var reference = value.reference();
        var canonicalRecordId = reference.target().id();
        return new FileView(
                Long.toString(asset.id()),
                asset.originalName(),
                asset.mediaType(),
                asset.size(),
                asset.sha256(),
                Long.toString(asset.uploaderMemberId()),
                asset.createdAt().toString(),
                Long.toString(reference.createdByMemberId()),
                reference.createdAt().toString(),
                "/openapi/v1/modules/" + moduleCode + "/records/" + canonicalRecordId
                        + "/files/" + asset.id() + "/content");
    }

    private static RuntimeRecordFileActor actor(
            Session session,
            String moduleCode,
            String recordId
    ) {
        try {
            return new RuntimeRecordFileActor(
                    session.systemId(),
                    session.tenantId(),
                    session.serviceMemberId(),
                    session.permissions(),
                    moduleCode,
                    positiveId(recordId, "RECORD_NOT_FOUND"));
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw invalid("OPENAPI_FILE_REQUEST_INVALID", exception.getMessage());
        }
    }

    private static byte[] decode(String contentBase64) {
        if (contentBase64 == null || contentBase64.length() > MAX_CANONICAL_BASE64_CHARS) {
            throw invalid("OPENAPI_FILE_CONTENT_INVALID",
                    "contentBase64 must contain canonical Base64 for at most 512 KiB");
        }
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(contentBase64);
        } catch (IllegalArgumentException exception) {
            throw invalid("OPENAPI_FILE_CONTENT_INVALID", "contentBase64 is not valid Base64");
        }
        if (decoded.length > MAX_DECODED_BYTES) {
            throw invalid("OPENAPI_FILE_SIZE_LIMIT", "Decoded file content exceeds 512 KiB");
        }
        if (!Base64.getEncoder().encodeToString(decoded).equals(contentBase64)) {
            throw invalid("OPENAPI_FILE_CONTENT_INVALID", "contentBase64 must use canonical padded Base64");
        }
        return decoded;
    }

    private static String uploadScope(Session session, String moduleCode, String recordId) {
        return session.systemId() + ":" + session.tenantId() + ":" + session.applicationId() + ":"
                + recordId + ":" + sha256(moduleCode.getBytes(StandardCharsets.UTF_8)).substring(0, 32);
    }

    private static long positiveId(String value, String code) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException exception) {
            throw new BusinessException(code, "Resource was not found in the current scope", HttpStatus.NOT_FOUND);
        }
    }

    private static String safeMediaType(String value) {
        try {
            return MediaType.parseMediaType(value).toString();
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new BusinessException(
                    "IDEMPOTENCY_KEY_REQUIRED", "A valid Idempotency-Key is required", HttpStatus.BAD_REQUEST);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("OpenAPI record file value must be JSON serializable", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read idempotent OpenAPI file response", exception);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static <T> T call(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (FileDomainException exception) {
            var status = switch (exception.code()) {
                case "FILE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
                case "FILE_FORBIDDEN" -> HttpStatus.FORBIDDEN;
                case "RECORD_FILE_PAGE_INVALID", "RECORD_FILE_SIZE_INVALID" -> HttpStatus.BAD_REQUEST;
                case "FILE_STILL_REFERENCED", "FILE_CONCURRENT_MODIFICATION", "FILE_VERSION_CONFLICT" ->
                        HttpStatus.CONFLICT;
                default -> HttpStatus.UNPROCESSABLE_ENTITY;
            };
            throw new BusinessException(exception.code(), exception.getMessage(), status);
        } catch (IllegalArgumentException exception) {
            throw invalid("OPENAPI_FILE_REQUEST_INVALID", exception.getMessage());
        }
    }

    private static BusinessException invalid(String code, String message) {
        return new BusinessException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    public record Session(
            long applicationId,
            long accountId,
            long systemId,
            long tenantId,
            long serviceMemberId,
            Set<String> permissions,
            String requestId,
            String traceId
    ) {
        public Session {
            if (applicationId <= 0 || accountId <= 0 || systemId <= 0 || tenantId <= 0 || serviceMemberId <= 0) {
                throw new IllegalArgumentException("OpenAPI file session ids must be positive");
            }
            permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
            requestId = requiredContext(requestId, "requestId");
            traceId = requiredContext(traceId, "traceId");
        }
    }

    public record UploadCommand(String originalName, String mediaType, String contentBase64) { }

    public record FileView(
            String fileId,
            String originalName,
            String mediaType,
            long sizeBytes,
            String sha256,
            String uploaderMemberId,
            String createdAt,
            String referencedByMemberId,
            String referencedAt,
            String downloadPath
    ) { }

    public record PageView(List<FileView> items, int page, int size, long total) {
        public PageView {
            items = List.copyOf(items);
        }
    }

    public record DownloadView(
            String fileId,
            String originalName,
            String mediaType,
            long sizeBytes,
            String sha256,
            String contentDisposition,
            byte[] content
    ) {
        public DownloadView {
            content = Objects.requireNonNull(content, "content").clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    private record UploadIdentity(
            String originalName,
            String mediaType,
            int sizeBytes,
            String sha256
    ) { }

    private static String requiredContext(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new IllegalArgumentException(name + " must contain 1..64 characters");
        }
        return value;
    }
}
