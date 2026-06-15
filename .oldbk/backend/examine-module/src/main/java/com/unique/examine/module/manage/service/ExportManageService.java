package com.unique.examine.module.manage.service;

import java.util.List;

import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.module.manage.bo.ExportJobActionBO;
import com.unique.examine.module.manage.bo.ExportJobCreateBO;
import com.unique.examine.module.manage.bo.ExportJobQueryBO;
import com.unique.examine.module.manage.bo.ExportTemplateSaveBO;
import com.unique.examine.module.manage.vo.ExportJobDetailVO;
import com.unique.examine.module.manage.vo.ExportJobListItemVO;
import com.unique.examine.module.manage.vo.ExportTemplateVO;

/**
 * 导出模板和任务管理服务。
 */
public interface ExportManageService {

    /**
     * 查询导出模板列表。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @return 导出模板列表
     */
    List<ExportTemplateVO> listTemplates(Long systemId, Long moduleId);

    /**
     * 创建导出模板。
     *
     * @param systemId 系统 ID
     * @param saveBO 保存入参
     * @return 导出模板
     */
    ExportTemplateVO createTemplate(Long systemId, ExportTemplateSaveBO saveBO);

    /**
     * 更新导出模板。
     *
     * @param systemId 系统 ID
     * @param templateId 模板 ID
     * @param saveBO 保存入参
     * @return 导出模板
     */
    ExportTemplateVO updateTemplate(Long systemId, Long templateId, ExportTemplateSaveBO saveBO);

    /**
     * 创建导出任务。
     *
     * @param systemId 系统 ID
     * @param createBO 创建入参
     * @return 导出任务详情
     */
    ExportJobDetailVO createJob(Long systemId, ExportJobCreateBO createBO);

    /**
     * 查询导出任务列表。
     *
     * @param systemId 系统 ID
     * @param queryBO 查询入参
     * @return 导出任务分页
     */
    PageResult<ExportJobListItemVO> listJobs(Long systemId, ExportJobQueryBO queryBO);

    /**
     * 查询导出任务详情。
     *
     * @param systemId 系统 ID
     * @param jobId 任务 ID
     * @return 导出任务详情
     */
    ExportJobDetailVO jobDetail(Long systemId, Long jobId);

    /**
     * 重试导出任务。
     *
     * @param systemId 系统 ID
     * @param jobId 任务 ID
     * @param actionBO 动作入参
     * @return 导出任务详情
     */
    ExportJobDetailVO retryJob(Long systemId, Long jobId, ExportJobActionBO actionBO);

    /**
     * 取消导出任务。
     *
     * @param systemId 系统 ID
     * @param jobId 任务 ID
     * @param actionBO 动作入参
     * @return 导出任务详情
     */
    ExportJobDetailVO cancelJob(Long systemId, Long jobId, ExportJobActionBO actionBO);
}
