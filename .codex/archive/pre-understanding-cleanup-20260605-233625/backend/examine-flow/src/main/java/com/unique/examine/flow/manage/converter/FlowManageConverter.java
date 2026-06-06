package com.unique.examine.flow.manage.converter;

import com.unique.examine.flow.base.entity.Instance;
import com.unique.examine.flow.base.entity.Task;
import com.unique.examine.flow.base.entity.Template;
import com.unique.examine.flow.base.entity.TemplateVersion;
import com.unique.examine.flow.manage.vo.FlowManageVO;

/**
 * 流程实体转换器。
 */
public final class FlowManageConverter {

    private FlowManageConverter() {
    }

    /**
     * 转换模板。
     *
     * @param entity 模板实体
     * @return 流程出参
     */
    public static FlowManageVO fromTemplate(Template entity) {
        FlowManageVO vo = new FlowManageVO();
        vo.setId(entity.getId());
        vo.setTenantId(entity.getTenantId());
        vo.setAppId(entity.getAppId());
        vo.setModuleId(entity.getModuleId());
        vo.setCode(entity.getTemplateCode());
        vo.setName(entity.getTemplateName());
        vo.setStatus(entity.getStatus());
        vo.setTemplateVersionId(entity.getPublishedVersionId());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 转换模板版本。
     *
     * @param entity 模板版本实体
     * @return 流程出参
     */
    public static FlowManageVO fromTemplateVersion(TemplateVersion entity) {
        FlowManageVO vo = new FlowManageVO();
        vo.setId(entity.getId());
        vo.setTemplateId(entity.getTemplateId());
        vo.setCode(String.valueOf(entity.getVersionNo()));
        vo.setName("v" + entity.getVersionNo());
        vo.setStatus(entity.getStatus());
        vo.setGraphJson(entity.getGraphJson());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 转换流程实例。
     *
     * @param entity 实例实体
     * @return 流程出参
     */
    public static FlowManageVO fromInstance(Instance entity) {
        FlowManageVO vo = new FlowManageVO();
        vo.setId(entity.getId());
        vo.setTenantId(entity.getTenantId());
        vo.setModuleId(entity.getModuleId());
        vo.setRecordId(entity.getRecordId());
        vo.setTemplateId(entity.getTemplateId());
        vo.setTemplateVersionId(entity.getTemplateVersionId());
        vo.setCode(entity.getCurrentNodeKey());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 转换任务。
     *
     * @param entity 任务实体
     * @return 流程出参
     */
    public static FlowManageVO fromTask(Task entity) {
        FlowManageVO vo = new FlowManageVO();
        vo.setId(entity.getId());
        vo.setTemplateId(entity.getInstanceId());
        vo.setCode(entity.getNodeKey());
        vo.setName(entity.getTaskName());
        vo.setAssigneeId(entity.getAssigneeId());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getHandledAt());
        return vo;
    }
}
