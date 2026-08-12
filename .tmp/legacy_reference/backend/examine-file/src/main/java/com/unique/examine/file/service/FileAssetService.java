package com.unique.examine.file.service;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.file.domain.FileActor;
import com.unique.examine.file.domain.FileAsset;
import com.unique.examine.file.domain.FileAssetPage;
import com.unique.examine.file.domain.FileDomainException;
import com.unique.examine.file.domain.FilePreview;
import com.unique.examine.file.domain.FileStorageStatus;
import com.unique.examine.file.domain.FileThumbnail;
import com.unique.examine.file.domain.MultipartUploadSession;
import com.unique.examine.file.port.FileAssetRepository;
import com.unique.examine.file.port.FileContentStore;
import com.unique.examine.file.port.FileStorageStatusProvider;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.transaction.annotation.Transactional;

@Transactional
public class FileAssetService {
    public static final String CREATE = "FILE_CREATE";
    public static final String READ = "FILE_READ";
    public static final String REFERENCE = "FILE_REFERENCE";
    public static final String MANAGE = "FILE_MANAGE";
    public static final int MAX_CONTENT_BYTES = 20 * 1024 * 1024;
    public static final int MAX_MULTIPART_BYTES = 100 * 1024 * 1024;
    public static final int MULTIPART_PART_BYTES = 5 * 1024 * 1024;
    public static final int MAX_MULTIPART_PARTS = 20;
    public static final Duration MULTIPART_TTL = Duration.ofMinutes(30);
    public static final int MAX_ACTIVE_MULTIPART_PER_MEMBER = 3;
    public static final int MAX_MULTIPART_SESSIONS = 128;
    public static final Duration MULTIPART_TERMINAL_RETENTION = Duration.ofMinutes(5);
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_THUMBNAIL_DIMENSION = 1024;
    private static final long MAX_IMAGE_PIXELS = 40_000_000L;
    private static final Set<String> PREVIEW_MEDIA = Set.of(
            "image/jpeg", "image/png", "image/gif", "application/pdf");
    private static final Set<String> THUMBNAIL_MEDIA = Set.of(
            "image/jpeg", "image/png", "image/gif");

    private final FileAssetRepository repository;
    private final FileContentStore contentStore;
    private final FileStorageStatusProvider storageStatus;
    private final Clock clock;
    private final ConcurrentHashMap<String, MultipartUploadSession> multipartUploads =
            new ConcurrentHashMap<>();

    public FileAssetService(FileAssetRepository repository, FileContentStore contentStore, Clock clock) {
        this(repository, contentStore, () -> unavailableStorageStatus(), clock);
    }

    public FileAssetService(
            FileAssetRepository repository,
            FileContentStore contentStore,
            FileStorageStatusProvider storageStatus,
            Clock clock
    ) {
        this.repository = required(repository, "repository");
        this.contentStore = required(contentStore, "content store");
        this.storageStatus = required(storageStatus, "storage status provider");
        this.clock = required(clock, "clock");
    }

    public FileAssetPage list(
            FileActor actor,
            int page,
            int size,
            String keyword,
            String mediaType
    ) {
        requireRead(actor);
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw error("FILE_PAGE_INVALID", "File page must be positive and contain at most 100 items");
        }
        var normalizedKeyword = optionalText(keyword, "keyword", 200);
        var normalizedMediaType = mediaTypeFilter(mediaType);
        return repository.findPage(actor.systemId(), actor.tenantId(), normalizedKeyword,
                normalizedMediaType, page, size);
    }

    public FileStorageStatus storageStatus(FileActor actor) {
        requirePermission(actor, MANAGE);
        try {
            var value = storageStatus.status();
            return value == null ? unavailableStorageStatus() : value;
        } catch (RuntimeException unavailable) {
            return unavailableStorageStatus();
        }
    }

    public FileAsset register(FileActor actor, String originalName, String mediaType, byte[] content) {
        return registerBounded(actor, originalName, mediaType, content, MAX_CONTENT_BYTES,
                "File content exceeds the 20 MiB single-upload limit");
    }

    private FileAsset registerBounded(
            FileActor actor,
            String originalName,
            String mediaType,
            byte[] content,
            int maxBytes,
            String limitMessage
    ) {
        requirePermission(actor, CREATE);
        validateName(originalName);
        if (content == null) {
            throw new IllegalArgumentException("File content is required");
        }
        if (content.length > maxBytes) {
            throw error("FILE_SIZE_LIMIT", limitMessage);
        }
        var id = repository.nextId();
        var objectKey = "system/" + actor.systemId() + "/tenant/" + actor.tenantId() + "/file/" + id;
        var asset = new FileAsset(id, actor.systemId(), actor.tenantId(), actor.memberId(), objectKey,
                originalName, mediaType, content.length, sha256(content), Instant.now(clock), Map.of(), 1);
        contentStore.put(objectKey, content);
        try {
            return repository.save(asset);
        } catch (RuntimeException failure) {
            contentStore.delete(objectKey);
            throw failure;
        }
    }

    public FileAsset registerAndReference(
            FileActor actor,
            String originalName,
            String mediaType,
            byte[] content,
            AggregateRef target
    ) {
        requirePermission(actor, CREATE);
        requirePermission(actor, REFERENCE);
        if (target == null) {
            throw new IllegalArgumentException("Reference target is required");
        }
        var created = register(actor, originalName, mediaType, content);
        try {
            return addReference(actor, created.id(), target);
        } catch (RuntimeException failure) {
            compensateFailedAttach(created, target, failure);
            throw failure;
        }
    }

    public FileAsset addReference(FileActor actor, long fileId, AggregateRef target) {
        requirePermission(actor, REFERENCE);
        if (target == null) {
            throw new IllegalArgumentException("Reference target is required");
        }
        var asset = scopedAsset(actor, fileId);
        var updated = asset.addReference(target, actor.memberId(), Instant.now(clock));
        return updated == asset ? asset : repository.save(updated);
    }

    public FileAsset removeReference(FileActor actor, long fileId, AggregateRef target) {
        var asset = scopedAsset(actor, fileId);
        var reference = asset.references().get(target);
        if (reference == null) {
            return asset;
        }
        if (!actor.has(MANAGE)
                && (!actor.has(REFERENCE) || reference.createdByMemberId() != actor.memberId())) {
            throw error("FILE_FORBIDDEN", "Only the reference creator or a file manager can remove it");
        }
        return repository.save(asset.removeReference(target));
    }

    public FileAsset get(FileActor actor, long fileId) {
        var asset = scopedAsset(actor, fileId);
        if (actor.memberId() != asset.uploaderMemberId() && !actor.has(READ) && !actor.has(MANAGE)) {
            throw error("FILE_FORBIDDEN", "Missing permission: " + READ);
        }
        return asset;
    }

    public byte[] readContent(FileActor actor, long fileId) {
        var asset = get(actor, fileId);
        var content = contentStore.read(asset.objectKey())
                .orElseThrow(() -> error("FILE_CONTENT_MISSING", "File content is missing"));
        verifyContent(asset, content);
        return content;
    }

    public FilePreview preview(FileActor actor, long fileId) {
        var asset = get(actor, fileId);
        var mediaType = asset.mediaType().toLowerCase(Locale.ROOT);
        if (!PREVIEW_MEDIA.contains(mediaType)) {
            throw error("FILE_PREVIEW_UNSUPPORTED",
                    "Only JPEG, PNG, GIF, and PDF files support inline preview");
        }
        var content = readContent(actor, fileId);
        verifySignature(mediaType, content);
        return new FilePreview(asset.originalName(), mediaType, content);
    }

    public FileThumbnail thumbnail(FileActor actor, long fileId, int maxWidth, int maxHeight) {
        if (maxWidth < 1 || maxHeight < 1
                || maxWidth > MAX_THUMBNAIL_DIMENSION || maxHeight > MAX_THUMBNAIL_DIMENSION) {
            throw error("FILE_THUMBNAIL_SIZE_INVALID",
                    "Thumbnail dimensions must be between 1 and 1024 pixels");
        }
        var asset = get(actor, fileId);
        var mediaType = asset.mediaType().toLowerCase(Locale.ROOT);
        if (!THUMBNAIL_MEDIA.contains(mediaType)) {
            throw error("FILE_THUMBNAIL_UNSUPPORTED",
                    "Only JPEG, PNG, and GIF files support thumbnails");
        }
        var content = readContent(actor, fileId);
        verifySignature(mediaType, content);
        return renderThumbnail(asset.originalName(), content, maxWidth, maxHeight);
    }

    public MultipartUploadSession.Snapshot initializeMultipart(
            FileActor actor,
            String originalName,
            String mediaType,
            long sizeBytes,
            String sha256
    ) {
        requirePermission(actor, CREATE);
        validateName(originalName);
        if (originalName == null || originalName.isBlank() || originalName.length() > 255
                || mediaType == null || mediaType.isBlank() || mediaType.length() > 150
                || sizeBytes < 1 || sizeBytes > MAX_MULTIPART_BYTES
                || !validSha256(sha256)) {
            throw error("FILE_MULTIPART_REQUEST_INVALID",
                    "Multipart metadata, total size, or SHA-256 is invalid");
        }
        synchronized (multipartUploads) {
            cleanupExpired();
            var activeForMember = multipartUploads.values().stream()
                    .filter(session -> session.matches(actor) && session.isOpen())
                    .count();
            if (activeForMember >= MAX_ACTIVE_MULTIPART_PER_MEMBER) {
                throw error("FILE_MULTIPART_MEMBER_LIMIT",
                        "A member can have at most 3 active multipart uploads");
            }
            if (multipartUploads.size() >= MAX_MULTIPART_SESSIONS) {
                throw error("FILE_MULTIPART_GLOBAL_LIMIT",
                        "The server multipart session limit was reached");
            }
            var now = Instant.now(clock);
            var partCount = Math.toIntExact((sizeBytes + MULTIPART_PART_BYTES - 1)
                    / MULTIPART_PART_BYTES);
            if (partCount > MAX_MULTIPART_PARTS) {
                throw error("FILE_MULTIPART_PART_LIMIT", "Multipart upload exceeds 20 parts");
            }
            MultipartUploadSession session;
            do {
                var uploadId = UUID.randomUUID().toString();
                session = new MultipartUploadSession(uploadId, actor.systemId(), actor.tenantId(),
                        actor.memberId(), originalName, mediaType, sizeBytes, sha256,
                        MULTIPART_PART_BYTES, partCount, now, now.plus(MULTIPART_TTL));
            } while (multipartUploads.putIfAbsent(session.snapshot(actor).uploadId(), session) != null);
            return session.snapshot(actor);
        }
    }

    public MultipartUploadSession.PartReceipt uploadPart(
            FileActor actor,
            String uploadId,
            int partNumber,
            byte[] content,
            String partSha256
    ) {
        requirePermission(actor, CREATE);
        var session = multipart(actor, uploadId);
        return session.putPart(actor, partNumber, content, normalizeSha256(partSha256));
    }

    public FileAsset completeMultipart(
            FileActor actor,
            String uploadId,
            String sha256
    ) {
        requirePermission(actor, CREATE);
        var session = multipart(actor, uploadId);
        synchronized (session) {
            var snapshot = session.snapshot(actor);
            if (snapshot.status() == MultipartUploadSession.State.COMPLETED) {
                return session.completedAsset(actor);
            }
            var content = session.assemble(actor, sha256);
            try {
                var asset = registerBounded(actor, session.originalName(), session.mediaType(), content,
                        MAX_MULTIPART_BYTES,
                        "File content exceeds the 100 MiB multipart-upload limit");
                session.complete(actor, asset, Instant.now(clock));
                return asset;
            } finally {
                Arrays.fill(content, (byte) 0);
            }
        }
    }

    public MultipartUploadSession.AbortReceipt abortMultipart(
            FileActor actor,
            String uploadId
    ) {
        requirePermission(actor, CREATE);
        return multipart(actor, uploadId).abort(actor, Instant.now(clock));
    }

    public FileAsset delete(FileActor actor, long fileId) {
        var asset = scopedAsset(actor, fileId);
        if (actor.memberId() != asset.uploaderMemberId() && !actor.has(MANAGE)) {
            throw error("FILE_FORBIDDEN", "Only the uploader or a file manager can delete this file");
        }
        if (asset.referenceCount() > 0) {
            throw error("FILE_STILL_REFERENCED", "Referenced files cannot be deleted");
        }
        repository.delete(asset.systemId(), asset.tenantId(), asset.id(), asset.version());
        contentStore.delete(asset.objectKey());
        return asset;
    }

    private FileAsset scopedAsset(FileActor actor, long fileId) {
        var asset = repository.findById(actor.systemId(), actor.tenantId(), fileId)
                .orElseThrow(() -> error("FILE_NOT_FOUND", "File was not found"));
        return asset;
    }

    private MultipartUploadSession multipart(FileActor actor, String uploadId) {
        if (uploadId == null || !uploadId.matches("[A-Za-z0-9-]{1,64}")) {
            throw error("FILE_MULTIPART_NOT_FOUND", "Multipart upload was not found");
        }
        var session = multipartUploads.get(uploadId);
        if (session == null || !session.matches(actor)) {
            cleanupExpired();
            throw error("FILE_MULTIPART_NOT_FOUND", "Multipart upload was not found");
        }
        var now = Instant.now(clock);
        if (session.isOpenExpired(now)) {
            session.expire();
            multipartUploads.remove(uploadId, session);
            throw error("FILE_MULTIPART_EXPIRED", "Multipart upload expired");
        }
        if (session.isRetentionExpired(now, MULTIPART_TERMINAL_RETENTION)) {
            multipartUploads.remove(uploadId, session);
            throw error("FILE_MULTIPART_NOT_FOUND", "Multipart upload was not found");
        }
        cleanupExpired();
        return session;
    }

    private void cleanupExpired() {
        var now = Instant.now(clock);
        multipartUploads.forEach((id, session) -> {
            if (session.isRetentionExpired(now, MULTIPART_TERMINAL_RETENTION)
                    && multipartUploads.remove(id, session)) {
                if (session.isOpen()) session.expire();
            }
        });
    }

    private void compensateFailedAttach(
            FileAsset created,
            AggregateRef target,
            RuntimeException failure
    ) {
        try {
            var persisted = repository.findById(created.systemId(), created.tenantId(), created.id());
            if (persisted.isPresent()) {
                var current = persisted.orElseThrow();
                if (current.references().containsKey(target)) {
                    current = repository.save(current.removeReference(target));
                }
                if (current.referenceCount() == 0) {
                    repository.delete(
                            current.systemId(),
                            current.tenantId(),
                            current.id(),
                            current.version());
                }
            }
        } catch (RuntimeException compensationFailure) {
            failure.addSuppressed(compensationFailure);
        } finally {
            try {
                contentStore.delete(created.objectKey());
            } catch (RuntimeException compensationFailure) {
                failure.addSuppressed(compensationFailure);
            }
        }
    }

    private static void requirePermission(FileActor actor, String permission) {
        if (actor == null || !actor.has(permission)) {
            throw error("FILE_FORBIDDEN", "Missing permission: " + permission);
        }
    }

    private static void requireRead(FileActor actor) {
        if (actor == null || !actor.has(READ) && !actor.has(MANAGE)) {
            throw error("FILE_FORBIDDEN", "Missing permission: " + READ);
        }
    }

    private static void validateName(String originalName) {
        if (originalName != null && (originalName.contains("/") || originalName.contains("\\"))) {
            throw error("FILE_NAME_INVALID", "File name must not contain a path");
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void verifyContent(FileAsset asset, byte[] content) {
        if (content.length != asset.size() || !sha256(content).equalsIgnoreCase(asset.sha256())) {
            throw error("FILE_CONTENT_INTEGRITY_FAILED", "Stored file content failed integrity verification");
        }
    }

    private static void verifySignature(String mediaType, byte[] content) {
        var valid = switch (mediaType) {
            case "image/jpeg" -> startsWith(content, new int[]{0xff, 0xd8, 0xff});
            case "image/png" -> startsWith(content,
                    new int[]{0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            case "image/gif" -> startsWith(content, "GIF87a".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                    || startsWith(content, "GIF89a".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            case "application/pdf" -> startsWith(content,
                    "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            default -> false;
        };
        if (!valid) {
            throw error("FILE_CONTENT_TYPE_MISMATCH",
                    "Stored content does not match its declared preview media type");
        }
    }

    private static boolean startsWith(byte[] content, int[] prefix) {
        if (content.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (Byte.toUnsignedInt(content[index]) != prefix[index]) return false;
        }
        return true;
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (content[index] != prefix[index]) return false;
        }
        return true;
    }

    private static FileThumbnail renderThumbnail(
            String originalName,
            byte[] content,
            int maxWidth,
            int maxHeight
    ) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) throw invalidImage();
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidImage();
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                var sourceWidth = reader.getWidth(0);
                var sourceHeight = reader.getHeight(0);
                if (sourceWidth < 1 || sourceHeight < 1
                        || (long) sourceWidth * sourceHeight > MAX_IMAGE_PIXELS) {
                    throw error("FILE_THUMBNAIL_SOURCE_TOO_LARGE",
                            "Image dimensions exceed the safe thumbnail limit");
                }
                var ratio = Math.min((double) maxWidth / sourceWidth,
                        (double) maxHeight / sourceHeight);
                ratio = Math.min(1D, ratio);
                var targetWidth = Math.max(1, (int) Math.round(sourceWidth * ratio));
                var targetHeight = Math.max(1, (int) Math.round(sourceHeight * ratio));
                var sample = Math.max(1, (int) Math.floor(Math.max(
                        (double) sourceWidth / Math.max(targetWidth, 1),
                        (double) sourceHeight / Math.max(targetHeight, 1))));
                var parameters = reader.getDefaultReadParam();
                parameters.setSourceSubsampling(sample, sample, 0, 0);
                var source = reader.read(0, parameters);
                if (source == null) throw invalidImage();
                var target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = target.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                            RenderingHints.VALUE_RENDER_QUALITY);
                    graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
                } finally {
                    graphics.dispose();
                    source.flush();
                }
                try (var output = new ByteArrayOutputStream()) {
                    if (!ImageIO.write(target, "png", output)) throw invalidImage();
                    return new FileThumbnail(originalName, "image/png", targetWidth, targetHeight,
                            output.toByteArray());
                } finally {
                    target.flush();
                }
            } finally {
                reader.dispose();
            }
        } catch (FileDomainException domain) {
            throw domain;
        } catch (IOException | RuntimeException malformed) {
            throw invalidImage();
        }
    }

    private static FileDomainException invalidImage() {
        return error("FILE_THUMBNAIL_INVALID_IMAGE", "Image content could not be decoded safely");
    }

    private static String optionalText(String value, String name, int max) {
        if (value == null || value.isBlank()) return null;
        var normalized = value.strip();
        if (normalized.length() > max || normalized.indexOf('\0') >= 0) {
            throw error("FILE_FILTER_INVALID", "File " + name + " filter is invalid");
        }
        return normalized;
    }

    private static String mediaTypeFilter(String value) {
        var normalized = optionalText(value, "mediaType", 150);
        if (normalized == null) return null;
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9!#$&^_.+-]*/(?:\\*|[a-z0-9][a-z0-9!#$&^_.+-]*)")) {
            throw error("FILE_FILTER_INVALID", "File mediaType filter is invalid");
        }
        return normalized;
    }

    private static boolean validSha256(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{64}");
    }

    private static String normalizeSha256(String value) {
        if (value == null || value.isBlank()) return null;
        if (!validSha256(value)) {
            throw error("FILE_MULTIPART_PART_HASH_MISMATCH", "Multipart part SHA-256 is invalid");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static FileStorageStatus unavailableStorageStatus() {
        return new FileStorageStatus(FileStorageStatus.Mode.UNAVAILABLE, "Not configured",
                MAX_CONTENT_BYTES, MAX_MULTIPART_BYTES, MULTIPART_PART_BYTES, false);
    }

    private static FileDomainException error(String code, String message) {
        return new FileDomainException(code, message);
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
