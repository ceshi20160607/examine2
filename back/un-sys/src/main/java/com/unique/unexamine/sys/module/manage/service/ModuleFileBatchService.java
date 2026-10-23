package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.unique.unexamine.core.file.base.entity.UnCommonFile;
import com.unique.unexamine.core.file.base.service.IUnCommonFileService;
import com.unique.unexamine.core.field.FileBatchId;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 附件字段保存批次 ID，同批文件直接归属一条模块记录。 */
@Service
public class ModuleFileBatchService {
    private final IUnCommonFileService files;

    /** 文件归属读写均使用生成的基础 Service。 */
    public ModuleFileBatchService(IUnCommonFileService files) { this.files = files; }

    /** 保存前锁定并校验整批文件；另一记录或租户的批次不能被复用。 */
    public void validate(Long systemId, Long dataTenantId, Long recordId, Long accountId,
                         List<UnModuleField> fields, Map<String, Object> values) {
        Set<String> used = new HashSet<>();
        for (UnModuleField field : fields) {
            if (!fileField(field)) continue;
            String batchId = batchId(values.get(field.getFieldCode()));
            if (batchId == null) continue;
            if (!used.add(batchId)) throw new IllegalArgumentException("同一文件批次不能配置到多个字段");
            List<UnCommonFile> batch = files.list(new LambdaQueryWrapper<UnCommonFile>()
                    .eq(UnCommonFile::getBatchId, batchId).orderByAsc(UnCommonFile::getId).last("FOR UPDATE"));
            if (batch.isEmpty()) throw new IllegalArgumentException(field.getFieldName() + "：文件批次不存在");
            for (UnCommonFile file : batch) {
                if (!"ACTIVE".equals(file.getStatus()) || !"SYSTEM".equals(file.getScopeType())
                        || !systemId.equals(file.getSystemId()) || !dataTenantId.equals(file.getTenantId())
                        || file.getModuleId() != null && !field.getModuleId().equals(file.getModuleId())
                        || file.getDataId() != null && !java.util.Objects.equals(recordId, file.getDataId()))
                    throw new IllegalArgumentException(field.getFieldName() + "：文件批次不属于当前记录或不可用");
                if (file.getDataId() == null && !accountId.equals(file.getUploaderAccountId()))
                    throw new IllegalArgumentException(field.getFieldName() + "：不能关联他人未归属的文件");
                if ("IMAGE".equals(field.getFieldType()) && !file.getContentType().startsWith("image/"))
                    throw new IllegalArgumentException(field.getFieldName() + "：仅允许图片文件");
            }
        }
    }

    /** 主记录和字段值写入后，同事务设置新批次归属并解除旧批次归属。 */
    public void sync(UnModuleRecord record, List<UnModuleField> fields, Map<String, Object> values) {
        Set<String> selected = new HashSet<>();
        for (UnModuleField field : fields) if (fileField(field)) {
            String batchId = batchId(values.get(field.getFieldCode()));
            if (batchId != null) selected.add(batchId);
        }
        List<UnCommonFile> linked = files.list(new LambdaQueryWrapper<UnCommonFile>()
                .eq(UnCommonFile::getModuleId, record.getModuleId()).eq(UnCommonFile::getDataId, record.getId()));
        for (UnCommonFile file : linked) if (!selected.contains(file.getBatchId())) {
            files.update(new LambdaUpdateWrapper<UnCommonFile>().eq(UnCommonFile::getId, file.getId())
                    .set(UnCommonFile::getModuleId, null).set(UnCommonFile::getDataId, null));
        }
        for (String batchId : selected) {
            files.update(new LambdaUpdateWrapper<UnCommonFile>().eq(UnCommonFile::getSystemId, record.getSystemId())
                    .eq(UnCommonFile::getTenantId, record.getTenantId()).eq(UnCommonFile::getBatchId, batchId)
                    .set(UnCommonFile::getModuleId, record.getModuleId()).set(UnCommonFile::getDataId, record.getId()));
        }
    }

    private boolean fileField(UnModuleField field) {
        return Set.of("ATTACHMENT", "IMAGE", "FILE_GROUP").contains(field.getFieldType());
    }

    private String batchId(Object raw) {
        if (raw == null) return null;
        return FileBatchId.require(raw);
    }
}
