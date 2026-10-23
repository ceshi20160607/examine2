package com.unique.unexamine.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** 本地存储实现；根目录由部署配置指定，对象键不包含上传文件名。 */
public final class LocalFileStorage implements FileStorage {
    private final Path root;

    /** 归一化根目录，后续对象键只能落在该目录内。 */
    public LocalFileStorage(Path root) { this.root = root.toAbsolutePath().normalize(); }

    /** 先写临时文件，再移入随机对象键；上传失败不留下半成品。 */
    @Override
    public StoredFile store(InputStream content) throws IOException {
        Files.createDirectories(root);
        String id = UUID.randomUUID().toString();
        String key = id.substring(0, 2) + "/" + id;
        Path temporary = Files.createTempFile(root, "upload-", ".part");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0;
            try (InputStream source = content; var target = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = source.read(buffer)) != -1) {
                    target.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                    size += count;
                }
            }
            Path destination = path(key);
            Files.createDirectories(destination.getParent());
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            return new StoredFile(key, size, HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** 只接受服务端生成的 UUID 对象键，避免路径穿越。 */
    private Path path(String key) {
        if (key == null || !key.matches("[0-9a-f]{2}/[0-9a-f-]{36}"))
            throw new IllegalArgumentException("文件对象键无效");
        Path result = root.resolve(key).normalize();
        if (!result.startsWith(root)) throw new IllegalArgumentException("文件对象键超出存储目录");
        return result;
    }

    @Override public InputStream open(String key) throws IOException { return Files.newInputStream(path(key)); }
    @Override public void delete(String key) throws IOException { Files.deleteIfExists(path(key)); }
}
