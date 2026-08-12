package com.unique.examine.file.service;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import com.unique.examine.file.domain.FileActor;
import com.unique.examine.file.domain.FileAsset;
import com.unique.examine.file.domain.FileDomainException;
import com.unique.examine.file.domain.RuntimeRecordFile;
import com.unique.examine.file.domain.RuntimeRecordFileActor;
import com.unique.examine.file.domain.RuntimeRecordFilePage;
import com.unique.examine.file.port.FileAssetRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.transaction.annotation.Transactional;

@Transactional
public class RuntimeRecordFileService {
    public static final String TARGET_TYPE = "RUNTIME_RECORD";
    public static final int MAX_BUNDLE_FILES = 50;
    public static final long MAX_BUNDLE_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;

    private final FileAssetService assets;
    private final FileAssetRepository repository;
    private final RuntimeRecordAccessFacade recordAccess;

    public RuntimeRecordFileService(
            FileAssetService assets,
            FileAssetRepository repository,
            RuntimeRecordAccessFacade recordAccess
    ) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.recordAccess = Objects.requireNonNull(recordAccess, "recordAccess");
    }

    @Transactional(readOnly = true)
    public RuntimeRecordFilePage page(RuntimeRecordFileActor actor, int page, int size) {
        var context = resolve(actor);
        requirePermission(context.fileActor(), FileAssetService.READ);
        RuntimeRecordFilePage.validate(page, size);
        return repository.findReferencePage(
                actor.systemId(),
                actor.tenantId(),
                context.target(),
                page,
                size);
    }

    public RuntimeRecordFile attach(
            RuntimeRecordFileActor actor,
            String originalName,
            String mediaType,
            byte[] content
    ) {
        var context = resolve(actor);
        var attached = assets.registerAndReference(
                context.fileActor(),
                originalName,
                mediaType,
                content,
                context.target());
        return referenced(attached, context.target());
    }

    /**
     * Revalidates the current record scope and both owner permissions without
     * creating metadata or content. OpenAPI idempotent replays use this before
     * returning a stored response so revoked access cannot be bypassed by an
     * old key.
     */
    @Transactional(readOnly = true)
    public void requireAttachAccess(RuntimeRecordFileActor actor) {
        var context = resolve(actor);
        requirePermission(context.fileActor(), FileAssetService.CREATE);
        requirePermission(context.fileActor(), FileAssetService.REFERENCE);
    }

    @Transactional(readOnly = true)
    public RuntimeRecordFileDownload download(RuntimeRecordFileActor actor, long fileId) {
        var context = resolve(actor);
        requirePermission(context.fileActor(), FileAssetService.READ);
        var file = requireReferenced(actor, fileId, context.target());
        var content = assets.readContent(context.fileActor(), fileId);
        verifyContent(file.asset(), content);
        return new RuntimeRecordFileDownload(file, content);
    }

    @Transactional(readOnly = true)
    public RuntimeRecordFileBundle bundle(RuntimeRecordFileActor actor) {
        var context = resolve(actor);
        requirePermission(context.fileActor(), FileAssetService.READ);
        var page = repository.findReferencePage(
                actor.systemId(),
                actor.tenantId(),
                context.target(),
                1,
                RuntimeRecordFilePage.MAX_SIZE);
        if (page.total() > MAX_BUNDLE_FILES || page.items().size() > MAX_BUNDLE_FILES) {
            throw bundleLimit("A record file bundle accepts at most "
                    + MAX_BUNDLE_FILES + " files");
        }
        validateMetadataSize(page.items());

        var names = new HashSet<String>();
        var entries = new ArrayList<BundleEntry>(page.items().size());
        for (var item : page.items()) {
            var asset = item.asset();
            var content = assets.readContent(context.fileActor(), asset.id());
            verifyContent(asset, content);
            entries.add(new BundleEntry(
                    uniqueEntryName(asset.originalName(), asset.id(), names),
                    asset.createdAt().toEpochMilli(),
                    content));
        }
        return new RuntimeRecordFileBundle(
                context.target().id(),
                page.items().size(),
                zip(entries));
    }

    public RuntimeRecordFile detach(RuntimeRecordFileActor actor, long fileId) {
        var context = resolve(actor);
        var current = requireReferenced(actor, fileId, context.target());
        assets.removeReference(context.fileActor(), fileId, context.target());
        return current;
    }

    private ResolvedContext resolve(RuntimeRecordFileActor actor) {
        Objects.requireNonNull(actor, "actor");
        var access = recordAccess.requireView(actor.accessRequest());
        return new ResolvedContext(
                actor.fileActor(),
                new AggregateRef(TARGET_TYPE, access.recordId()));
    }

    private RuntimeRecordFile requireReferenced(
            RuntimeRecordFileActor actor,
            long fileId,
            AggregateRef target
    ) {
        var asset = repository.findById(actor.systemId(), actor.tenantId(), fileId)
                .orElseThrow(RuntimeRecordFileService::notFound);
        var reference = asset.references().get(target);
        if (reference == null) {
            throw notFound();
        }
        return new RuntimeRecordFile(asset, reference);
    }

    private static RuntimeRecordFile referenced(FileAsset asset, AggregateRef target) {
        var reference = asset.references().get(target);
        if (reference == null) {
            throw new IllegalStateException("Registered file is missing its runtime record reference");
        }
        return new RuntimeRecordFile(asset, reference);
    }

    private static void requirePermission(FileActor actor, String permission) {
        if (!actor.has(permission)) {
            throw new FileDomainException(
                    "FILE_FORBIDDEN",
                    "Missing permission: " + permission);
        }
    }

    private static void validateMetadataSize(List<RuntimeRecordFile> files) {
        long total = 0;
        for (var file : files) {
            var size = file.asset().size();
            if (size > MAX_BUNDLE_UNCOMPRESSED_BYTES - total) {
                throw bundleLimit("Record file bundle uncompressed size exceeds 100 MiB");
            }
            total += size;
        }
    }

    private static void verifyContent(FileAsset asset, byte[] content) {
        if (content.length != asset.size() || !sha256(content).equals(asset.sha256())) {
            throw new FileDomainException(
                    "FILE_CONTENT_INTEGRITY_FAILED",
                    "Stored content failed integrity verification for file " + asset.id());
        }
    }

    private static byte[] zip(List<BundleEntry> entries) {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var entry : entries) {
                var zipEntry = new ZipEntry(entry.name());
                zipEntry.setTime(entry.createdAtEpochMillis());
                zip.putNextEntry(zipEntry);
                zip.write(entry.content());
                zip.closeEntry();
            }
        } catch (IOException exception) {
            var failure = new FileDomainException(
                    "FILE_BUNDLE_CREATE_FAILED",
                    "Record file bundle could not be created");
            failure.initCause(exception);
            throw failure;
        }
        return output.toByteArray();
    }

    private static String uniqueEntryName(
            String originalName,
            long fileId,
            Set<String> used
    ) {
        var base = safeEntryName(originalName, fileId);
        if (used.add(base)) {
            return base;
        }
        int attempt = 1;
        while (true) {
            var candidate = suffixedName(base, fileId, attempt);
            if (used.add(candidate)) {
                return candidate;
            }
            attempt++;
        }
    }

    private static String safeEntryName(String originalName, long fileId) {
        var normalized = originalName == null ? "" : originalName.replace('\\', '/');
        var slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        var safe = new StringBuilder();
        normalized.codePoints().forEach(codePoint -> {
            var type = Character.getType(codePoint);
            if (type != Character.CONTROL
                    && type != Character.FORMAT
                    && codePoint != '/'
                    && codePoint != '\\') {
                safe.appendCodePoint(codePoint);
            }
        });
        var name = safe.toString().trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            return "file-" + fileId;
        }
        return name;
    }

    private static String suffixedName(String base, long fileId, int attempt) {
        var suffix = "-" + fileId + (attempt == 1 ? "" : "-" + attempt);
        var dot = base.lastIndexOf('.');
        if (dot > 0) {
            return base.substring(0, dot) + suffix + base.substring(dot);
        }
        return base + suffix;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static FileDomainException bundleLimit(String message) {
        return new FileDomainException("FILE_BUNDLE_LIMIT", message);
    }

    private static FileDomainException notFound() {
        return new FileDomainException(
                "FILE_NOT_FOUND",
                "File was not found in this runtime record");
    }

    private record ResolvedContext(
            FileActor fileActor,
            AggregateRef target
    ) {
    }

    private record BundleEntry(
            String name,
            long createdAtEpochMillis,
            byte[] content
    ) {
    }

    public record RuntimeRecordFileDownload(
            RuntimeRecordFile file,
            byte[] content
    ) {
        public RuntimeRecordFileDownload {
            Objects.requireNonNull(file, "file");
            content = Objects.requireNonNull(content, "content").clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    public record RuntimeRecordFileBundle(
            String recordId,
            int fileCount,
            byte[] content
    ) {
        public RuntimeRecordFileBundle {
            if (recordId == null || !recordId.matches("^[1-9][0-9]{0,18}$")
                    || fileCount < 0 || fileCount > MAX_BUNDLE_FILES) {
                throw new IllegalArgumentException("Runtime record file bundle metadata is invalid");
            }
            content = Objects.requireNonNull(content, "content").clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
