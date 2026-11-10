package com.unique.unexamine.sys.file.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.field.FileBatchId;
import com.unique.unexamine.core.event.BusinessEventContext;
import com.unique.unexamine.core.event.BusinessEventPublisher;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.file.base.entity.UnCommonFile;
import com.unique.unexamine.core.file.base.service.IUnCommonFileService;
import com.unique.unexamine.file.FileStorage;
import com.unique.unexamine.sys.file.manage.dto.FileManageDto.FileContent;
import com.unique.unexamine.sys.file.manage.dto.FileManageDto.FileView;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.manage.service.ModuleRecordService;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 系统文件业务归属、上传者访问与引用检查；字节存储由 un-file starter 提供。 */
@Service
public class FileManageService {
    private final SystemManageService systems;
    private final IUnCommonFileService files;
    private final FileStorage storage;
    private final IUnModuleRecordService records;
    private final ModuleRecordService recordBusiness;
    private final BusinessEventPublisher eventPublisher;

    /** 基础元数据服务和存储接口由容器注入。 */
    public FileManageService(SystemManageService systems, IUnCommonFileService files,
                             FileStorage storage, IUnModuleRecordService records,
                             ModuleRecordService recordBusiness, BusinessEventPublisher eventPublisher) {
        this.systems = systems; this.files = files; this.storage = storage;
        this.records = records; this.recordBusiness = recordBusiness;
        this.eventPublisher = eventPublisher;
    }

    /** 新批次生成独立 UUID；同批后续上传仅允许原上传者操作未关联批次。 */
    @Transactional(rollbackFor = Exception.class)
    public FileView upload(String token, Long systemId, Long tenantId, String batchId,
                           MultipartFile upload) throws IOException {
        var context = systems.context(token, systemId, tenantId);
        Long accountId = systems.accountId(token, systemId);
        if (batchId != null) {
            FileBatchId.require(batchId);
            var existing = files.list(new LambdaQueryWrapper<UnCommonFile>()
                    .eq(UnCommonFile::getBatchId, batchId).orderByAsc(UnCommonFile::getId).last("FOR UPDATE"));
            if (existing.isEmpty() || existing.stream().anyMatch(file -> !"ACTIVE".equals(file.getStatus())
                    || !"SYSTEM".equals(file.getScopeType()) || !systemId.equals(file.getSystemId())
                    || !context.tenantId().equals(file.getTenantId())
                    || !accountId.equals(file.getUploaderAccountId()) || file.getDataId() != null))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "文件批次不可继续上传");
        } else {
            batchId = FileBatchId.create();
        }
        if (upload == null || upload.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "上传文件不能为空");
        String name = upload.getOriginalFilename();
        if (name == null || name.isBlank()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "文件名不能为空");
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isBlank() || name.length() > 255) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "文件名长度无效");
        var stored = storage.store(upload.getInputStream());
        try {
            UnCommonFile file = new UnCommonFile();
            file.setScopeType("SYSTEM"); file.setSystemId(systemId); file.setTenantId(context.tenantId());
            file.setUploaderAccountId(accountId); file.setOriginalName(name);
            String contentType = upload.getContentType();
            file.setContentType(contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType);
            file.setSizeBytes(stored.size()); file.setSha256(stored.sha256());
            file.setStorageType("LOCAL"); file.setStorageKey(stored.key()); file.setStatus("ACTIVE");
            file.setBatchId(batchId);
            files.addOrUpdate(file);
            eventPublisher.publish(new BusinessEventContext("FILE_UPLOADED", systemId, context.tenantId(),
                    file.getModuleId(), file.getDataId(), accountId, null, java.util.Map.of(),
                    java.util.Map.of("fileId", file.getId(), "batchId", file.getBatchId(),
                            "originalName", file.getOriginalName(), "sizeBytes", file.getSizeBytes())));
            return view(file);
        } catch (RuntimeException ex) {
            try { storage.delete(stored.key()); } catch (IOException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
    }

    /** 只列当前租户本人上传的可用文件。 */
    public Page<FileView> queryPageList(String token, Long systemId, Long tenantId, SearchBO search) {
        var context = systems.context(token, systemId, tenantId);
        Long accountId = systems.accountId(token, systemId);
        SearchBO criteria = search == null ? new SearchBO() : search;
        var where = new LambdaQueryWrapper<UnCommonFile>().eq(UnCommonFile::getScopeType,"SYSTEM")
                .eq(UnCommonFile::getSystemId,systemId).eq(UnCommonFile::getTenantId,context.tenantId())
                .eq(UnCommonFile::getUploaderAccountId,accountId).eq(UnCommonFile::getStatus,"ACTIVE")
                // 历史数据可能在记录删除前已建立关联，显式过滤已删除记录避免绕过业务记录逻辑删除。
                .and(wrapper -> wrapper.isNull(UnCommonFile::getDataId)
                        .or().apply("EXISTS (SELECT 1 FROM un_module_record r WHERE r.id = data_id AND r.deleted_time IS NULL)"))
                .orderByDesc(UnCommonFile::getCreateTime);
        var result = files.page(criteria.parse(),where);
        return new Page<FileView>(result.getCurrent(), result.getSize(), result.getTotal()).setRecords(result.getRecords().stream().map(this::view).toList());
    }

    /** 上传者读取本租户文件；业务记录共享访问由记录所属模块另行授权。 */
    public FileView queryById(String token, Long systemId, Long tenantId, Long fileId) {
        return view(accessible(token, systemId, tenantId, fileId));
    }

    /** 附件字段给出批次 UUID 后，一次鉴权即可取得同批可见文件。 */
    public List<FileView> batch(String token, Long systemId, Long tenantId, String batchId) {
        FileBatchId.require(batchId);
        var batch = files.list(new LambdaQueryWrapper<UnCommonFile>()
                .eq(UnCommonFile::getSystemId, systemId).eq(UnCommonFile::getBatchId, batchId)
                .eq(UnCommonFile::getStatus, "ACTIVE").orderByAsc(UnCommonFile::getId));
        if (batch.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "文件批次不存在");
        UnCommonFile first = accessible(token, systemId, tenantId, batch.get(0));
        if (batch.stream().anyMatch(file -> !first.getTenantId().equals(file.getTenantId())
                || !java.util.Objects.equals(first.getModuleId(), file.getModuleId())
                || !java.util.Objects.equals(first.getDataId(), file.getDataId())
                || !first.getUploaderAccountId().equals(file.getUploaderAccountId())))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "文件批次归属不一致");
        return batch.stream().map(this::view).toList();
    }

    /** 所有内容出口先校验上传者及作用域，再打开私有对象。 */
    public FileContent content(String token, Long systemId, Long tenantId, Long fileId) throws IOException {
        var file = accessible(token, systemId, tenantId, fileId);
        return new FileContent(view(file), storage.open(file.getStorageKey()));
    }

    /** 浏览器仅内联常见安全图片和 PDF；其他格式提示使用下载。 */
    public FileContent preview(String token, Long systemId, Long tenantId, Long fileId) throws IOException {
        var file = accessible(token, systemId, tenantId, fileId);
        if (!List.of("image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf")
                .contains(file.getContentType().toLowerCase(java.util.Locale.ROOT)))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "该格式无法预览，请下载文件");
        return new FileContent(view(file), storage.open(file.getStorageKey()));
    }

    /** 已被业务对象引用的文件不能删除；保留删除状态便于审计和故障排查。 */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void delete(String token, Long systemId, Long tenantId, Long fileId) throws IOException {
        owned(token, systemId, tenantId, fileId);
        var file = files.getOne(new LambdaQueryWrapper<UnCommonFile>()
                .eq(UnCommonFile::getId, fileId).last("FOR UPDATE"), false);
        if (file == null || !"ACTIVE".equals(file.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "文件不存在");
        if (file.getDataId() != null)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "文件仍被业务对象引用");
        // 公共文件与业务数据一样使用逻辑删除；保留私有存储对象供审计追溯。
        files.removeById(file.getId());
    }

    /** 上传者和租户必须同时匹配，避免猜测文件 ID 读取其他租户对象。 */
    private UnCommonFile owned(String token, Long systemId, Long tenantId, Long fileId) {
        var context = systems.context(token, systemId, tenantId);
        Long accountId = systems.accountId(token, systemId);
        UnCommonFile file = files.getById(fileId);
        if (file == null || !"ACTIVE".equals(file.getStatus()) || !"SYSTEM".equals(file.getScopeType())
                || !systemId.equals(file.getSystemId()) || !context.tenantId().equals(file.getTenantId())
                || !accountId.equals(file.getUploaderAccountId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "文件不存在");
        return file;
    }

    /** 引用文件按记录详情和字段可读性重新鉴权；共享撤销后即时失效。 */
    private UnCommonFile accessible(String token, Long systemId, Long tenantId, Long fileId) {
        return accessible(token, systemId, tenantId, files.getById(fileId));
    }

    /** 未关联文件属于上传者；关联后统一按记录及字段读取权限判断。 */
    private UnCommonFile accessible(String token, Long systemId, Long tenantId, UnCommonFile file) {
        var context = systems.context(token, systemId, tenantId);
        Long accountId = systems.accountId(token, systemId);
        if (file == null || !"ACTIVE".equals(file.getStatus()) || !"SYSTEM".equals(file.getScopeType())
                || !systemId.equals(file.getSystemId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "文件不存在");
        if (file.getDataId() == null && context.tenantId().equals(file.getTenantId())
                && accountId.equals(file.getUploaderAccountId())) return file;
        if (file.getModuleId() != null && file.getDataId() != null) {
            UnModuleRecord record = records.getById(file.getDataId());
            if (record == null || !file.getTenantId().equals(record.getTenantId())
                    || !file.getModuleId().equals(record.getModuleId()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "文件不存在");
            try {
                var information = recordBusiness.information(token, systemId, tenantId,
                        record.getModuleId(), record.getId());
                if (information.fields().stream().anyMatch(item -> file.getBatchId().equals(item.value())))
                    return file;
            } catch (BaseException ex) {
                if (ex.getCode() != SystemCodeEnum.SYSTEM_NO_AUTH.getCode()
                        && ex.getCode() != SystemCodeEnum.SYSTEM_NO_FOUND.getCode()) throw ex;
            }
        }
        throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "文件不存在");
    }

    private FileView view(UnCommonFile file) {
        return new FileView(file.getId(), file.getBatchId(), file.getOriginalName(), file.getContentType(),
                file.getSizeBytes(), file.getSha256(), file.getStatus());
    }
}
