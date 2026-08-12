package com.unique.examine.file.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Member-scoped, bounded transient multipart state. */
public final class MultipartUploadSession {
    private final String uploadId;
    private final long systemId;
    private final long tenantId;
    private final long memberId;
    private final String originalName;
    private final String mediaType;
    private final long sizeBytes;
    private final String sha256;
    private final int partSizeBytes;
    private final int partCount;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Map<Integer, Part> parts = new LinkedHashMap<>();
    private State state = State.OPEN;
    private FileAsset completedAsset;
    private Instant terminalAt;

    public MultipartUploadSession(
            String uploadId,
            long systemId,
            long tenantId,
            long memberId,
            String originalName,
            String mediaType,
            long sizeBytes,
            String sha256,
            int partSizeBytes,
            int partCount,
            Instant createdAt,
            Instant expiresAt
    ) {
        if (uploadId == null || uploadId.isBlank() || uploadId.length() > 64
                || systemId <= 0 || tenantId <= 0 || memberId <= 0
                || originalName == null || originalName.isBlank() || originalName.length() > 255
                || mediaType == null || mediaType.isBlank() || mediaType.length() > 150
                || sizeBytes < 1 || !validSha256(sha256)
                || partSizeBytes < 1 || partCount < 1
                || createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Multipart upload session is invalid");
        }
        this.uploadId = uploadId;
        this.systemId = systemId;
        this.tenantId = tenantId;
        this.memberId = memberId;
        this.originalName = originalName.strip();
        this.mediaType = mediaType.strip().toLowerCase(java.util.Locale.ROOT);
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256.toLowerCase(java.util.Locale.ROOT);
        this.partSizeBytes = partSizeBytes;
        this.partCount = partCount;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public synchronized PartReceipt putPart(
            FileActor actor,
            int partNumber,
            byte[] content,
            String claimedSha256
    ) {
        requireOwner(actor);
        requireOpen();
        if (partNumber < 1 || partNumber > partCount || content == null) {
            throw error("FILE_MULTIPART_PART_INVALID", "Multipart part is invalid");
        }
        var expectedSize = expectedPartSize(partNumber);
        if (content.length != expectedSize) {
            throw error("FILE_MULTIPART_PART_SIZE_MISMATCH",
                    "Multipart part size does not match the initialized session");
        }
        var digest = digest(content);
        if (claimedSha256 != null && (!validSha256(claimedSha256)
                || !digest.equalsIgnoreCase(claimedSha256))) {
            throw error("FILE_MULTIPART_PART_HASH_MISMATCH",
                    "Multipart part SHA-256 does not match its content");
        }
        var existing = parts.get(partNumber);
        if (existing != null) {
            if (existing.sizeBytes() == content.length && existing.sha256().equals(digest)) {
                return receipt(existing, true);
            }
            throw error("FILE_MULTIPART_PART_CONFLICT",
                    "A different multipart part already uses this part number");
        }
        var stored = new Part(partNumber, content.length, digest, content);
        parts.put(partNumber, stored);
        return receipt(stored, false);
    }

    public synchronized byte[] assemble(FileActor actor, String claimedSha256) {
        requireOwner(actor);
        requireOpen();
        if (!validSha256(claimedSha256) || !sha256.equalsIgnoreCase(claimedSha256)) {
            throw error("FILE_MULTIPART_HASH_MISMATCH",
                    "Multipart completion SHA-256 does not match the initialized session");
        }
        if (parts.size() != partCount) {
            throw error("FILE_MULTIPART_INCOMPLETE", "Not all multipart parts were uploaded");
        }
        var content = new byte[Math.toIntExact(sizeBytes)];
        int offset = 0;
        for (int number = 1; number <= partCount; number++) {
            var part = parts.get(number);
            if (part == null) {
                throw error("FILE_MULTIPART_INCOMPLETE", "Not all multipart parts were uploaded");
            }
            var bytes = part.copyContent();
            System.arraycopy(bytes, 0, content, offset, bytes.length);
            offset += bytes.length;
            Arrays.fill(bytes, (byte) 0);
        }
        if (offset != sizeBytes || !sha256.equals(digest(content))) {
            Arrays.fill(content, (byte) 0);
            throw error("FILE_MULTIPART_HASH_MISMATCH",
                    "Multipart content SHA-256 verification failed");
        }
        return content;
    }

    public synchronized void complete(FileActor actor, FileAsset asset, Instant completedAt) {
        requireOwner(actor);
        requireOpen();
        if (asset == null || asset.systemId() != systemId || asset.tenantId() != tenantId
                || asset.uploaderMemberId() != memberId || asset.size() != sizeBytes
                || !asset.sha256().equals(sha256) || completedAt == null) {
            throw new IllegalArgumentException("Completed multipart asset does not match its session");
        }
        completedAsset = asset;
        state = State.COMPLETED;
        terminalAt = completedAt;
        clearParts();
    }

    public synchronized AbortReceipt abort(FileActor actor, Instant abortedAt) {
        requireOwner(actor);
        if (abortedAt == null) {
            throw new IllegalArgumentException("Multipart abort timestamp is required");
        }
        if (state == State.COMPLETED) {
            throw error("FILE_MULTIPART_ALREADY_COMPLETED",
                    "Completed multipart uploads cannot be aborted");
        }
        if (state != State.ABORTED) {
            state = State.ABORTED;
            terminalAt = abortedAt;
            clearParts();
        }
        return new AbortReceipt(uploadId, State.ABORTED);
    }

    public synchronized void expire() {
        if (state == State.OPEN) {
            state = State.EXPIRED;
            clearParts();
        }
    }

    public synchronized Snapshot snapshot(FileActor actor) {
        requireOwner(actor);
        return new Snapshot(uploadId, originalName, mediaType, sizeBytes, sha256,
                partSizeBytes, partCount, expiresAt, state);
    }

    public synchronized FileAsset completedAsset(FileActor actor) {
        requireOwner(actor);
        return completedAsset;
    }

    public boolean matches(FileActor actor) {
        return actor != null && actor.systemId() == systemId && actor.tenantId() == tenantId
                && actor.memberId() == memberId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public String originalName() {
        return originalName;
    }

    public String mediaType() {
        return mediaType;
    }

    public synchronized State state() {
        return state;
    }

    public synchronized boolean isOpen() {
        return state == State.OPEN;
    }

    public synchronized boolean isOpenExpired(Instant now) {
        return state == State.OPEN && !now.isBefore(expiresAt);
    }

    public synchronized boolean isRetentionExpired(Instant now, java.time.Duration terminalRetention) {
        if (state == State.OPEN) return !now.isBefore(expiresAt);
        if (terminalAt == null) return true;
        return !now.isBefore(terminalAt.plus(terminalRetention));
    }

    private int expectedPartSize(int partNumber) {
        if (partNumber < partCount) return partSizeBytes;
        return Math.toIntExact(sizeBytes - (long) partSizeBytes * (partCount - 1));
    }

    private PartReceipt receipt(Part part, boolean replay) {
        return new PartReceipt(uploadId, part.partNumber(), part.sizeBytes(), part.sha256(), replay,
                parts.size(), partCount);
    }

    private void requireOwner(FileActor actor) {
        if (!matches(actor)) {
            throw error("FILE_MULTIPART_NOT_FOUND", "Multipart upload was not found");
        }
    }

    private void requireOpen() {
        if (state == State.ABORTED) {
            throw error("FILE_MULTIPART_ABORTED", "Multipart upload was aborted");
        }
        if (state == State.EXPIRED) {
            throw error("FILE_MULTIPART_EXPIRED", "Multipart upload expired");
        }
        if (state == State.COMPLETED) {
            throw error("FILE_MULTIPART_ALREADY_COMPLETED", "Multipart upload is already completed");
        }
    }

    private void clearParts() {
        for (var part : parts.values()) part.clear();
        parts.clear();
    }

    private static String digest(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static boolean validSha256(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{64}");
    }

    private static FileDomainException error(String code, String message) {
        return new FileDomainException(code, message);
    }

    private static final class Part {
        private final int partNumber;
        private final int sizeBytes;
        private final String sha256;
        private final byte[] content;

        private Part(int partNumber, int sizeBytes, String sha256, byte[] content) {
            this.partNumber = partNumber;
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
            this.content = content.clone();
        }

        int partNumber() { return partNumber; }
        int sizeBytes() { return sizeBytes; }
        String sha256() { return sha256; }
        byte[] copyContent() { return content.clone(); }
        void clear() { Arrays.fill(content, (byte) 0); }
    }

    public enum State {
        OPEN,
        COMPLETED,
        ABORTED,
        EXPIRED
    }

    public record Snapshot(
            String uploadId,
            String originalName,
            String mediaType,
            long sizeBytes,
            String sha256,
            int partSizeBytes,
            int partCount,
            Instant expiresAt,
            State status
    ) { }

    public record PartReceipt(
            String uploadId,
            int partNumber,
            int sizeBytes,
            String sha256,
            boolean replay,
            int uploadedPartCount,
            int partCount
    ) { }

    public record AbortReceipt(String uploadId, State status) { }
}
