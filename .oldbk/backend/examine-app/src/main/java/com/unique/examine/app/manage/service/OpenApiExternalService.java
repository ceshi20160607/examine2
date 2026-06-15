package com.unique.examine.app.manage.service;

import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.flow.manage.vo.FlowActionResultVO;
import com.unique.examine.module.manage.vo.RecordDetailVO;
import com.unique.examine.module.manage.vo.RecordListItemVO;
import com.unique.examine.module.manage.vo.RecordMutationResultVO;
import com.unique.examine.upload.manage.vo.FileAccessVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * OpenAPI 外部业务服务。
 */
public interface OpenApiExternalService {

    /**
     * 查询运行记录。
     */
    PageResult<RecordListItemVO> queryRecords(HttpServletRequest request, String rawBody);

    /**
     * 查询运行记录详情。
     */
    RecordDetailVO recordDetail(HttpServletRequest request, Long recordId, String moduleCode);

    /**
     * 创建运行记录。
     */
    RecordMutationResultVO createRecord(HttpServletRequest request, String rawBody);

    /**
     * 更新运行记录。
     */
    RecordMutationResultVO updateRecord(HttpServletRequest request, Long recordId, String rawBody);

    /**
     * 提交运行记录。
     */
    RecordMutationResultVO submitRecord(HttpServletRequest request, Long recordId, String rawBody);

    /**
     * 处理流程任务。
     */
    FlowActionResultVO handleTask(HttpServletRequest request, Long taskId, String rawBody);

    /**
     * 下载文件。
     */
    FileAccessVO downloadFile(HttpServletRequest request, Long fileId);
}
