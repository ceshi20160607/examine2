package com.unique.unexamine.core.ops;

import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** 平台和系统备份共用 SQL 文件存储；随机文件键只在有权下载的接口中解析。 */
@Service
public class OperationsSqlFileService {
    public record Stored(String key,long sizeBytes,String sha256) {}
    @FunctionalInterface public interface Writer { void write(OutputStream output) throws Exception; }

    private final Environment environment;
    public OperationsSqlFileService(Environment environment) { this.environment=environment; }

    /** 写完 SQL 后计算摘要并原子发布，失败时不留下半成品。 */
    public Stored create(Writer writer) {
        Path temporary=null;
        try {
            Files.createDirectories(root());
            temporary=Files.createTempFile(root(),"ops-",".part");
            try (OutputStream output=Files.newOutputStream(temporary)) { writer.write(output); }
            String key=UUID.randomUUID().toString();
            Path target=path(key);
            String sha=sha256(temporary);
            long size=Files.size(temporary);
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);
            return new Stored(key,size,sha);
        } catch (Exception ex) {
            if (ex instanceof BaseException business) throw business;
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"SQL 备份文件生成失败",ex);
        } finally {
            if (temporary!=null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) { }
        }
    }

    /** 下载时校验完整文件的 SHA-256，避免传出被截断或改写的备份。 */
    public Path verified(String key,String expectedSha256) {
        Path file=path(key);
        try {
            if (!Files.isRegularFile(file))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"备份文件不存在");
            if (expectedSha256==null || !sha256(file).equals(expectedSha256))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"备份文件摘要不匹配");
            return file;
        } catch (BaseException ex) { throw ex; }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"备份文件校验失败",ex); }
    }

    private Path path(String key) {
        if (key==null || !key.matches("[0-9a-f-]{36}"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"备份文件键无效");
        Path root=root(), file=root.resolve(key+".sql").normalize();
        if (!file.startsWith(root)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"备份文件路径无效");
        return file;
    }

    private Path root() {
        return Path.of(environment.getProperty("APP_OPS_BACKUP_ROOT","data/ops-backups")).toAbsolutePath().normalize();
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try (var input=Files.newInputStream(file)) {
            byte[] buffer=new byte[64*1024]; int count;
            while ((count=input.read(buffer))!=-1) digest.update(buffer,0,count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
