package com.unique.examine.file.adapter.local;

import com.unique.examine.file.port.FileContentStore;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

public final class LocalFileContentStore implements FileContentStore {
    private final Path root;

    public LocalFileContentStore(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("File storage root is required");
        }
        var normalized = root.toAbsolutePath().normalize();
        try {
            if (Files.isSymbolicLink(normalized)) {
                throw new IllegalArgumentException("File storage root must not be a symbolic link");
            }
            Files.createDirectories(normalized);
            this.root = normalized.toRealPath();
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to initialize local file storage", failure);
        }
    }

    @Override
    public void put(String objectKey, byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("File content is required");
        }
        var target = target(objectKey);
        var parent = target.getParent();
        var temporary = parent.resolve("." + target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(parent);
            rejectSymbolicLinks(target);
            Files.write(temporary, content);
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                exception.addSuppressed(ignored);
            }
            throw storageFailure("write", exception);
        }
    }

    @Override
    public Optional<byte[]> read(String objectKey) {
        var target = target(objectKey);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(target));
        } catch (IOException exception) {
            throw storageFailure("read", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        var target = target(objectKey);
        try {
            Files.deleteIfExists(target);
            removeEmptyParents(target.getParent());
        } catch (IOException exception) {
            throw storageFailure("delete", exception);
        }
    }

    private Path target(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("/")
                || objectKey.endsWith("/") || objectKey.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Object key is invalid");
        }
        for (var segment : objectKey.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Object key escapes the file storage root");
            }
            for (int index = 0; index < segment.length(); index++) {
                if (Character.isISOControl(segment.charAt(index))) {
                    throw new IllegalArgumentException("Object key is invalid");
                }
            }
        }
        var resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("Object key escapes the file storage root");
        }
        rejectSymbolicLinks(resolved);
        return resolved;
    }

    public boolean available() {
        return Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(root)
                && Files.isReadable(root)
                && Files.isWritable(root);
    }

    private void rejectSymbolicLinks(Path target) {
        var current = root;
        for (var segment : root.relativize(target)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("Object key traverses a symbolic link");
            }
        }
    }

    private void removeEmptyParents(Path directory) throws IOException {
        var current = directory;
        while (current != null && !current.equals(root) && current.startsWith(root)) {
            try (var entries = Files.list(current)) {
                if (entries.findAny().isPresent()) {
                    return;
                }
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }

    private static IllegalStateException storageFailure(String operation, IOException cause) {
        return new IllegalStateException("Unable to " + operation + " local file content", cause);
    }
}
