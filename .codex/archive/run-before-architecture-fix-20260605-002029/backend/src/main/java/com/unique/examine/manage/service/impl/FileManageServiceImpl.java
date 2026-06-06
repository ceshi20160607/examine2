package com.unique.examine.manage.service.impl;

import com.unique.examine.base.entity.FileObject;
import com.unique.examine.base.entity.FileRelation;
import com.unique.examine.base.entity.ImportExportTask;
import com.unique.examine.base.service.IFileObjectService;
import com.unique.examine.base.service.IFileRelationService;
import com.unique.examine.base.service.IImportExportTaskService;
import com.unique.examine.manage.bo.*;
import com.unique.examine.manage.converter.EntityMapConverter;
import com.unique.examine.manage.enums.ErrorCode;
import com.unique.examine.manage.enums.StatusEnums;
import com.unique.examine.manage.exception.BusinessException;
import com.unique.examine.manage.security.SecurityContext;
import com.unique.examine.manage.service.FileManageService;
import com.unique.examine.manage.service.PermissionService;
import com.unique.examine.manage.vo.PageResult;
import com.unique.examine.manage.vo.SimpleVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FileManageServiceImpl implements FileManageService {
    private final IFileObjectService fileObjectService;
    private final IFileRelationService fileRelationService;
    private final IImportExportTaskService importExportTaskService;
    private final PermissionService permissionService;
    private final EntityMapConverter converter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO createFile(FileObjectSaveBO bo) {
        permissionService.requireAction(bo.getSystemId(), bo.getTenantId(), "file:create");
        FileObject file = new FileObject();
        file.setSystemId(bo.getSystemId()); file.setTenantId(bo.getTenantId()); file.setStoragePath(bo.getStoragePath()); file.setFileName(bo.getFileName()); file.setFileSize(bo.getFileSize()); file.setContentType(bo.getContentType()); file.setStatus(StatusEnums.TEMP); file.setCreatedBy(SecurityContext.currentUser().getAccountId());
        fileObjectService.save(file);
        return converter.toSimple(file);
    }

    @Override
    public PageResult<SimpleVO> files(long pageNo, long pageSize, Long systemId, Long tenantId, String status) {
        permissionService.requireAction(systemId, tenantId, "file:view");
        IPage<FileObject> page = fileObjectService.page(Page.of(pageNo, pageSize), Wrappers.<FileObject>lambdaQuery().eq(FileObject::getSystemId, systemId).eq(FileObject::getTenantId, tenantId).eq(status != null && !status.isBlank(), FileObject::getStatus, status).orderByDesc(FileObject::getUpdatedAt));
        return new PageResult<>(pageNo, pageSize, page.getTotal(), page.getRecords().stream().map(converter::toSimple).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO link(FileRelationSaveBO bo) {
        permissionService.requireAction(bo.getSystemId(), bo.getTenantId(), "file:link");
        FileObject file = fileObjectService.getById(bo.getFileId());
        if (file == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在"); }
        FileRelation relation = new FileRelation();
        relation.setSystemId(bo.getSystemId()); relation.setTenantId(bo.getTenantId()); relation.setFileId(bo.getFileId()); relation.setRelationType(bo.getRelationType()); relation.setRelationId(bo.getRelationId());
        fileRelationService.save(relation);
        file.setStatus(StatusEnums.LINKED); fileObjectService.updateById(file);
        return converter.toSimple(relation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long fileId) {
        FileObject file = fileObjectService.getById(fileId);
        if (file == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在"); }
        permissionService.requireAction(file.getSystemId(), file.getTenantId(), "file:delete");
        file.setStatus(StatusEnums.DELETED); fileObjectService.updateById(file);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO createImportExportTask(ImportExportTaskSaveBO bo) {
        permissionService.requireAction(bo.getSystemId(), bo.getTenantId(), "import-export:create");
        ImportExportTask task = new ImportExportTask();
        task.setSystemId(bo.getSystemId()); task.setTenantId(bo.getTenantId()); task.setAppId(bo.getAppId()); task.setModuleId(bo.getModuleId()); task.setTaskType(bo.getTaskType()); task.setTemplateId(bo.getTemplateId()); task.setTaskStatus(StatusEnums.PENDING); task.setCreatedBy(SecurityContext.currentUser().getAccountId());
        importExportTaskService.save(task);
        return converter.toSimple(task);
    }

    @Override
    public PageResult<SimpleVO> importExportTasks(long pageNo, long pageSize, Long systemId, Long tenantId, Long moduleId, String taskType) {
        permissionService.requireAction(systemId, tenantId, "import-export:view");
        IPage<ImportExportTask> page = importExportTaskService.page(Page.of(pageNo, pageSize), Wrappers.<ImportExportTask>lambdaQuery().eq(ImportExportTask::getSystemId, systemId).eq(ImportExportTask::getTenantId, tenantId).eq(moduleId != null, ImportExportTask::getModuleId, moduleId).eq(taskType != null && !taskType.isBlank(), ImportExportTask::getTaskType, taskType).orderByDesc(ImportExportTask::getUpdatedAt));
        return new PageResult<>(pageNo, pageSize, page.getTotal(), page.getRecords().stream().map(converter::toSimple).toList());
    }
}
