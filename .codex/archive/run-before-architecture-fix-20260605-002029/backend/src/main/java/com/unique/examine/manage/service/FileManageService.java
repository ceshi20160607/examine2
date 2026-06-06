package com.unique.examine.manage.service;

import com.unique.examine.manage.bo.*;
import com.unique.examine.manage.vo.*;

public interface FileManageService {
    SimpleVO createFile(FileObjectSaveBO bo);
    PageResult<SimpleVO> files(long pageNo, long pageSize, Long systemId, Long tenantId, String status);
    SimpleVO link(FileRelationSaveBO bo);
    void delete(Long fileId);
    SimpleVO createImportExportTask(ImportExportTaskSaveBO bo);
    PageResult<SimpleVO> importExportTasks(long pageNo, long pageSize, Long systemId, Long tenantId, Long moduleId, String taskType);
}
