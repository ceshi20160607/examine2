package com.unique.unexamine.plat.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.ops.OperationsSqlFileService;
import com.unique.unexamine.plat.base.entity.UnPlatLogOpt;
import com.unique.unexamine.plat.base.entity.UnPlatOpsBackup;
import com.unique.unexamine.plat.base.service.IUnPlatLogOptService;
import com.unique.unexamine.plat.base.service.IUnPlatOpsBackupService;
import com.unique.unexamine.plat.manage.dto.PlatformBackupDto.View;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** 平台管理员导出整个项目数据库的结构和数据，备份产物为普通 SQL 文件。 */
@Service
public class PlatformBackupService {
    private final AuthManageService auth;
    private final IUnPlatOpsBackupService backups;
    private final IUnPlatLogOptService logs;
    private final OperationsSqlFileService files;
    private final Environment environment;

    public PlatformBackupService(AuthManageService auth,IUnPlatOpsBackupService backups,IUnPlatLogOptService logs,
                                 OperationsSqlFileService files,Environment environment) {
        this.auth=auth; this.backups=backups; this.logs=logs; this.files=files; this.environment=environment;
    }

    /** 平台管理员可核对平台和各系统备份元数据，不通过此入口下载系统备份。 */
    public Page<View> list(String token,SearchBO search) {
        auth.requirePlatformAdmin(token);
        SearchBO criteria=search==null?new SearchBO():search;
        Page<UnPlatOpsBackup> page=backups.page(criteria.parse(),new LambdaQueryWrapper<UnPlatOpsBackup>()
                .orderByDesc(UnPlatOpsBackup::getId));
        return new Page<View>(page.getCurrent(),page.getSize(),page.getTotal())
                .setRecords(page.getRecords().stream().map(this::view).toList());
    }

    /** 先记录发起人，再导出整库；只有完整 SQL 文件写入成功才标记可下载。 */
    public View create(String token) {
        Long actor=auth.requirePlatformAdmin(token).getId();
        UnPlatOpsBackup row=new UnPlatOpsBackup(); row.setScopeType("PLATFORM"); row.setBackupKind("PLATFORM_SQL");
        row.setStatus("CREATING"); row.setCreateAccountId(actor); row.setCreateTime(LocalDateTime.now());
        backups.addOrUpdate(row);
        audit(actor,"PLATFORM_BACKUP_START",row.getId(),"平台整库 SQL 导出开始");
        try {
            var stored=files.create(this::dumpDatabase);
            row.setArtifactKey(stored.key()); row.setSizeBytes(stored.sizeBytes()); row.setSha256(stored.sha256());
            row.setStatus("READY"); backups.addOrUpdate(row);
            audit(actor,"PLATFORM_BACKUP_READY",row.getId(),"平台整库 SQL 可下载");
            return view(row);
        } catch (RuntimeException ex) {
            row.setStatus("FAILED"); row.setErrorText("数据库导出或文件存储失败"); backups.addOrUpdate(row);
            audit(actor,"PLATFORM_BACKUP_FAILED",row.getId(),"平台整库 SQL 导出失败");
            throw ex;
        }
    }

    /** 下载前再次检查平台管理员身份、备份作用域和文件摘要。 */
    public Path download(String token,Long backupId) {
        Long actor=auth.requirePlatformAdmin(token).getId();
        UnPlatOpsBackup row=backups.queryById(backupId);
        if (row==null || !"PLATFORM".equals(row.getScopeType())
                || !"PLATFORM_SQL".equals(row.getBackupKind()) || !"READY".equals(row.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"可用的平台 SQL 备份不存在");
        Path file=files.verified(row.getArtifactKey(),row.getSha256());
        audit(actor,"PLATFORM_BACKUP_DOWNLOAD",row.getId(),"下载平台整库 SQL");
        return file;
    }

    private void dumpDatabase(OutputStream output) throws Exception {
        String url=environment.getProperty("spring.datasource.url");
        if (url==null || !url.startsWith("jdbc:mysql://"))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"数据库连接配置不支持 SQL 导出");
        java.net.URI uri=java.net.URI.create(url.substring(5));
        String database=databaseName(uri);
        String localClient=environment.getProperty("APP_OPS_MYSQLDUMP");
        List<String> options=List.of("--single-transaction","--quick","--no-tablespaces",
                "--skip-add-drop-table","--hex-blob","--default-character-set=utf8mb4",database);
        List<String> command=new ArrayList<>();
        if (localClient!=null && !localClient.isBlank()) {
            command.add(localClient);
            command.add("--user="+environment.getProperty("spring.datasource.username","root"));
            command.add("--host="+uri.getHost());
            command.add("--port="+(uri.getPort()<0?3306:uri.getPort()));
            command.addAll(options);
        } else {
            String container=environment.getProperty("APP_OPS_MYSQL_CONTAINER","mysql");
            if (!container.matches("[A-Za-z0-9_.-]+"))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"MySQL 容器名无效");
            command.addAll(List.of("docker","exec",container,"sh","-c",
                    "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysqldump -uroot "
                            +String.join(" ",options)));
        }
        Path error=Files.createTempFile("ops-mysqldump-",".log");
        Process process=null;
        try {
            ProcessBuilder builder=new ProcessBuilder(command).redirectError(error.toFile());
            if (localClient!=null && !localClient.isBlank())
                builder.environment().put("MYSQL_PWD",environment.getProperty("spring.datasource.password",""));
            process=builder.start();
            Process running=process;
            Thread watchdog=Thread.ofVirtual().start(() -> {
                try { Thread.sleep(TimeUnit.MINUTES.toMillis(30)); running.destroyForcibly(); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            });
            try {
                process.getInputStream().transferTo(output);
                int exit=process.waitFor();
                if (exit!=0) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"数据库导出失败，退出码 "+exit);
            } finally { watchdog.interrupt(); }
        } finally {
            if (process!=null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(error);
        }
    }

    private String databaseName(java.net.URI uri) {
        String name=uri.getPath().replaceFirst("^/","");
        if (!name.matches("[A-Za-z0-9_]+"))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"数据库名称无效");
        return name;
    }

    private View view(UnPlatOpsBackup row) {
        return new View(row.getId(),row.getScopeType(),row.getSystemId(),row.getBackupKind(),row.getStatus(),
                row.getSizeBytes(),row.getSha256(),row.getErrorText(),row.getCreateAccountId(),row.getCreateTime());
    }

    private void audit(Long actor,String action,Long id,String detail) {
        UnPlatLogOpt log=new UnPlatLogOpt(); log.setAccountId(actor); log.setActionCode(action);
        log.setObjectType("OPS_BACKUP"); log.setObjectId(id); log.setDetailJson("{\"summary\":\""+detail+"\"}");
        logs.addOrUpdate(log);
    }
}
