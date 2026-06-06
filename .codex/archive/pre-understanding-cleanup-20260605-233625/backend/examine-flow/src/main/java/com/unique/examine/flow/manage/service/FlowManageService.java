package com.unique.examine.flow.manage.service;

import com.unique.examine.flow.manage.bo.FlowStartBO;
import com.unique.examine.flow.manage.bo.FlowTaskHandleBO;
import com.unique.examine.flow.manage.bo.FlowTemplatePublishBO;
import com.unique.examine.flow.manage.bo.FlowTemplateSaveBO;
import com.unique.examine.flow.manage.dto.FlowTaskQueryDTO;
import com.unique.examine.flow.manage.vo.FlowManageVO;

import java.util.List;

/**
 * 流程管理服务。
 */
public interface FlowManageService {

    /**
     * 查询流程模板。
     *
     * @param moduleId 模块 ID
     * @return 模板列表
     */
    List<FlowManageVO> listTemplates(Long moduleId);

    /**
     * 创建流程模板。
     *
     * @param bo 模板入参
     * @return 模板信息
     */
    FlowManageVO createTemplate(FlowTemplateSaveBO bo);

    /**
     * 发布流程模板。
     *
     * @param bo 发布入参
     * @return 版本信息
     */
    FlowManageVO publishTemplate(FlowTemplatePublishBO bo);

    /**
     * 发起流程。
     *
     * @param bo 发起入参
     * @return 流程实例
     */
    FlowManageVO start(FlowStartBO bo);

    /**
     * 查询待办任务。
     *
     * @param dto 查询 DTO
     * @return 任务列表
     */
    List<FlowManageVO> listTasks(FlowTaskQueryDTO dto);

    /**
     * 处理任务。
     *
     * @param taskId 任务 ID
     * @param bo 处理入参
     * @return 任务信息
     */
    FlowManageVO handleTask(Long taskId, FlowTaskHandleBO bo);
}
