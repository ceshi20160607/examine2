package com.unique.unexamine.file.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.file.base.entity.FileObject;
import com.unique.unexamine.file.base.entity.FileReference;
import com.unique.unexamine.file.base.service.FileObjectBaseService;
import com.unique.unexamine.file.base.service.FileReferenceBaseService;
import com.unique.unexamine.flow.manage.FlowManageService;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecord;
import com.unique.unexamine.runtimedata.base.service.BusinessRecordBaseService;
import com.unique.unexamine.runtimedata.manage.RecordManageService;
import com.unique.unexamine.system.manage.SystemPermissionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** 记录附件的本地存储与数据库引用；下载和预览每次重新校验业务记录范围。 */
@Service
public class FileManageService {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private final SystemPermissionService access;
    private final RecordManageService recordViews;
    private final BusinessRecordBaseService records;
    private final FlowManageService flows;
    private final FileObjectBaseService files;
    private final FileReferenceBaseService references;
    private final Path root;

    public FileManageService(SystemPermissionService access, RecordManageService recordViews,
                             BusinessRecordBaseService records,
                             FlowManageService flows, FileObjectBaseService files,
                             FileReferenceBaseService references,
                             @Value("${APP_FILE_ROOT:../data/files}") String rootPath) {
        this.access = access;
        this.recordViews = recordViews;
        this.records = records;
        this.flows = flows;
        this.files = files;
        this.references = references;
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
    }

    /** 上传仅允许负责人修改未提交或已拒绝记录，落盘失败不写数据库引用。 */
    @Transactional
    public FileView upload(Long accountId, Long systemId, Long moduleId, Long recordId, MultipartFile part) {
        SystemPermissionService.Context context = access.requireModule(accountId, systemId, moduleId, "UPDATE");
        BusinessRecord record = recordViews.ownedForSubmission(context, moduleId, recordId);
        touchRecord(record, accountId);
        if (part == null || part.isEmpty() || part.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "附件不能为空且不能超过 10 MB");
        }
        String original = part.getOriginalFilename();
        original = original == null ? "未命名文件" : original.replace('\\', '/');
        original = original.substring(original.lastIndexOf('/') + 1).trim();
        if (original.isEmpty() || original.length() > 255 || original.indexOf('\r') >= 0 || original.indexOf('\n') >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件名无效");
        }
        byte[] bytes;
        try { bytes = part.getBytes(); }
        catch (IOException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取上传文件"); }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件大小无效");
        String key = UUID.randomUUID().toString().replace("-", "");
        Path path = root.resolve(key);
        try {
            Files.createDirectories(root);
            Files.write(path, bytes);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "附件写入失败");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) removeQuietly(path);
            }
        });
        FileObject file = new FileObject();
        file.setSystemId(systemId);
        file.setTenantId(context.tenant().getId());
        file.setStorageKey(key);
        file.setOriginalName(original);
        file.setContentType(safeType(bytes));
        file.setSizeBytes((long) bytes.length);
        file.setSha256Hex(sha256(bytes));
        file.setStatus("ACTIVE");
        file.setCreatedByAccountId(accountId);
        files.insert(file);
        FileReference reference = new FileReference();
        reference.setFileId(file.getId());
        reference.setRecordId(recordId);
        references.insert(reference);
        return view(file);
    }

    /** 列出当前有权查看的业务记录附件。 */
    public List<FileView> list(Long accountId, Long systemId, Long moduleId, Long recordId) {
        access.requireModule(accountId, systemId, moduleId, "VIEW");
        recordViews.detail(accountId, systemId, moduleId, recordId);
        return listRecord(recordId);
    }

    /** 审批任务明确授权后可查看该业务记录的附件。 */
    public List<FileView> listForTask(Long accountId, Long systemId, Long taskId) {
        FlowManageService.TaskDetail task = flows.detail(accountId, systemId, taskId);
        return listRecord(task.record().id());
    }

    /** 只有记录访问者或指定审批任务处理人可下载对应附件。 */
    public FileBytes read(Long accountId, Long systemId, Long moduleId, Long recordId,
                          Long fileId, boolean preview, boolean thumbnail) {
        access.requireModule(accountId, systemId, moduleId, "VIEW");
        recordViews.detail(accountId, systemId, moduleId, recordId);
        return readReferenced(systemId, recordId, fileId, preview, thumbnail);
    }

    /** 待办附件读取保留任务授权校验，不接受任意业务记录主键。 */
    public FileBytes readForTask(Long accountId, Long systemId, Long taskId,
                                 Long fileId, boolean preview, boolean thumbnail) {
        FlowManageService.TaskDetail task = flows.detail(accountId, systemId, taskId);
        return readReferenced(systemId, task.record().id(), fileId, preview, thumbnail);
    }

    /** 移除附件前确认当前记录可编辑、引用属于该记录，再于事务提交后删除实际文件。 */
    @Transactional
    public void remove(Long accountId, Long systemId, Long moduleId, Long recordId, Long fileId) {
        SystemPermissionService.Context context = access.requireModule(accountId, systemId, moduleId, "UPDATE");
        BusinessRecord record = recordViews.ownedForSubmission(context, moduleId, recordId);
        touchRecord(record, accountId);
        FileReference reference = requireReference(recordId, fileId);
        FileObject file = requireFile(systemId, fileId);
        references.deleteById(reference.getId());
        file.setStatus("DELETED");
        file.setDeleteTime(LocalDateTime.now());
        files.updateById(file);
        Path path = root.resolve(file.getStorageKey());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { removeQuietly(path); }
        });
    }

    private List<FileView> listRecord(Long recordId) {
        return references.selectList(new LambdaQueryWrapper<FileReference>()
                .eq(FileReference::getRecordId, recordId).orderByAsc(FileReference::getId))
                .stream().map(reference -> files.selectById(reference.getFileId()))
                .filter(file -> file != null && "ACTIVE".equals(file.getStatus())).map(this::view).toList();
    }

    private FileBytes readReferenced(Long systemId, Long recordId, Long fileId,
                                     boolean preview, boolean thumbnail) {
        requireReference(recordId, fileId);
        FileObject file = requireFile(systemId, fileId);
        Path path = root.resolve(file.getStorageKey());
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (thumbnail) {
                if (!file.getContentType().startsWith("image/")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "此文件没有图片缩略图");
                return new FileBytes(thumbnail(bytes), "image/png", file.getOriginalName(), true);
            }
            boolean inline = preview && (file.getContentType().startsWith("image/")
                    || "application/pdf".equals(file.getContentType()));
            if (preview && !inline) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "此文件不支持预览，请下载");
            return new FileBytes(bytes, file.getContentType(), file.getOriginalName(), inline);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件内容不可用");
        }
    }

    private FileReference requireReference(Long recordId, Long fileId) {
        FileReference reference = references.selectOne(new LambdaQueryWrapper<FileReference>()
                .eq(FileReference::getRecordId, recordId).eq(FileReference::getFileId, fileId));
        if (reference == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在");
        return reference;
    }

    private FileObject requireFile(Long systemId, Long fileId) {
        FileObject file = files.selectById(fileId);
        if (file == null || !systemId.equals(file.getSystemId()) || !"ACTIVE".equals(file.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在");
        }
        return file;
    }

    private FileView view(FileObject file) {
        return new FileView(file.getId(), file.getOriginalName(), file.getContentType(),
                file.getSizeBytes(), file.getCreateTime());
    }

    /** 附件变动占用记录乐观锁，避免并发发起审批后仍写入或移除附件。 */
    private void touchRecord(BusinessRecord record, Long accountId) {
        record.setUpdatedByAccountId(accountId);
        if (records.updateById(record) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "记录已变化，请刷新后重试附件操作");
        }
    }

    private String safeType(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 255) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
        if (bytes.length >= 3 && (bytes[0] & 255) == 0xff && (bytes[1] & 255) == 0xd8 && (bytes[2] & 255) == 0xff) return "image/jpeg";
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') return "image/gif";
        if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F') return "application/pdf";
        return "application/octet-stream";
    }

    private byte[] thumbnail(byte[] original) {
        try {
            BufferedImage source = ImageIO.read(new java.io.ByteArrayInputStream(original));
            if (source == null) throw new IOException("图片无法解码");
            double scale = Math.min(1.0, 240.0 / Math.max(source.getWidth(), source.getHeight()));
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = target.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
            graphics.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(target, "png", out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片无法生成缩略图");
        }
    }

    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("摘要算法不可用", exception); }
    }

    private void removeQuietly(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException ignored) { /* 数据库引用已移除；后续可清理孤立本地文件。 */ }
    }

    /** 页面展示的文件元数据不暴露磁盘路径。 */
    public record FileView(Long id, String name, String contentType, Long sizeBytes, LocalDateTime createTime) {}
    /** 已按当前请求权限读取的文件内容。 */
    public record FileBytes(byte[] bytes, String contentType, String name, boolean inline) {}
}
