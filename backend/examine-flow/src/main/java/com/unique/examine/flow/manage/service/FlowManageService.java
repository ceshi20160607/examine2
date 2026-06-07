package com.unique.examine.flow.manage.service;

import java.util.List;

import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.flow.manage.bo.FlowActionBO;
import com.unique.examine.flow.manage.bo.FlowBindingSaveBO;
import com.unique.examine.flow.manage.bo.FlowClaimBO;
import com.unique.examine.flow.manage.bo.FlowPublishBO;
import com.unique.examine.flow.manage.bo.FlowTaskQueryBO;
import com.unique.examine.flow.manage.bo.FlowTemplateGraphBO;
import com.unique.examine.flow.manage.bo.FlowTemplateSaveBO;
import com.unique.examine.flow.manage.bo.FlowTemplateStatusBO;
import com.unique.examine.flow.manage.bo.FlowWithdrawBO;
import com.unique.examine.flow.manage.vo.FlowActionResultVO;
import com.unique.examine.flow.manage.vo.FlowBindingVO;
import com.unique.examine.flow.manage.vo.FlowDiagramVO;
import com.unique.examine.flow.manage.vo.FlowHistoryItemVO;
import com.unique.examine.flow.manage.vo.FlowInstanceVO;
import com.unique.examine.flow.manage.vo.FlowPublishCheckResultVO;
import com.unique.examine.flow.manage.vo.FlowTaskDetailVO;
import com.unique.examine.flow.manage.vo.FlowTaskListItemVO;
import com.unique.examine.flow.manage.vo.FlowTemplateGraphVO;
import com.unique.examine.flow.manage.vo.FlowTemplateVO;

/**
 * 流程管理服务。
 */
public interface FlowManageService {

    /**
     * 查询流程模板。
     */
    List<FlowTemplateVO> listTemplates(Long systemId, String keyword, String status);

    /**
     * 创建流程模板草稿。
     */
    FlowTemplateVO createTemplate(Long systemId, FlowTemplateSaveBO saveBO);

    /**
     * 查询流程模板详情。
     */
    FlowTemplateVO templateDetail(Long systemId, Long templateId);

    /**
     * 保存流程图草稿。
     */
    FlowTemplateGraphVO saveGraph(Long systemId, Long templateId, FlowTemplateGraphBO graphBO);

    /**
     * 查询流程图。
     */
    FlowTemplateGraphVO templateGraph(Long systemId, Long templateId);

    /**
     * 发布检查。
     */
    FlowPublishCheckResultVO publishCheck(Long systemId, Long templateId);

    /**
     * 发布流程模板。
     */
    FlowTemplateVO publish(Long systemId, Long templateId, FlowPublishBO publishBO);

    /**
     * 绑定模块与流程。
     */
    FlowBindingVO bindModule(Long systemId, Long moduleId, FlowBindingSaveBO saveBO);

    /**
     * 查询待办任务。
     */
    PageResult<FlowTaskListItemVO> todoTasks(Long systemId, FlowTaskQueryBO queryBO);

    /**
     * 查询任务详情。
     */
    FlowTaskDetailVO taskDetail(Long systemId, Long taskId);

    /**
     * 处理任务。
     */
    FlowActionResultVO handleTask(Long systemId, Long taskId, FlowActionBO actionBO);

    /**
     * 撤回实例。
     */
    FlowActionResultVO withdraw(Long systemId, Long instanceId, FlowWithdrawBO withdrawBO);

    /**
     * 查询实例详情。
     */
    FlowInstanceVO instanceDetail(Long systemId, Long instanceId);

    /**
     * 查询实例图。
     */
    FlowDiagramVO instanceDiagram(Long systemId, Long instanceId);

    /**
     * 领取任务。
     */
    FlowActionResultVO claim(Long systemId, Long taskId, FlowClaimBO claimBO);

    /**
     * 取消领取任务。
     */
    FlowActionResultVO unclaim(Long systemId, Long taskId, FlowClaimBO claimBO);

    /**
     * 查询实例列表。
     */
    PageResult<FlowInstanceVO> listInstances(Long systemId, FlowTaskQueryBO queryBO);

    /**
     * 查询实例历史。
     */
    List<FlowHistoryItemVO> instanceHistory(Long systemId, Long instanceId);

    /**
     * 变更模板状态。
     */
    FlowTemplateVO changeTemplateStatus(Long systemId, Long templateId, FlowTemplateStatusBO statusBO);
}
