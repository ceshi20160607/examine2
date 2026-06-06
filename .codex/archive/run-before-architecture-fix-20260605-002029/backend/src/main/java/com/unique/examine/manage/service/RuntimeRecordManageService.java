package com.unique.examine.manage.service;

import com.unique.examine.manage.bo.*;
import com.unique.examine.manage.vo.*;

public interface RuntimeRecordManageService {
    PageResult<RecordVO> records(long pageNo, long pageSize, Long systemId, Long tenantId, Long appId, Long moduleId, String recordNo, String status);
    RecordVO create(BusinessRecordSaveBO bo);
    RecordVO createByOpenApi(BusinessRecordSaveBO bo);
    RecordVO update(Long recordId, BusinessRecordSaveBO bo);
    RecordVO detail(Long recordId);
    RecordVO detailByOpenApi(Long recordId);
    void delete(Long recordId);
    SimpleVO comment(RecordCommentSaveBO bo);
}
