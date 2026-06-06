package com.unique.examine.manage.service;

import com.unique.examine.manage.bo.*;
import com.unique.examine.manage.vo.*;

public interface WorkflowManageService {
    PageResult<SimpleVO> templates(long pageNo, long pageSize, Long systemId, Long tenantId, Long moduleId);
    SimpleVO saveTemplate(WorkflowTemplateSaveBO bo);
    SimpleVO saveVersion(WorkflowVersionSaveBO bo);
    SimpleVO publishVersion(Long versionId);
    SimpleVO start(WorkflowStartBO bo);
    PageResult<SimpleVO> tasks(long pageNo, long pageSize, Long assigneeId, String status);
    SimpleVO handleTask(Long taskId, WorkflowTaskActionBO bo);
}
