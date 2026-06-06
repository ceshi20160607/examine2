package com.unique.examine.module.manage.converter;

import com.unique.examine.module.base.entity.ExportJob;
import com.unique.examine.module.base.entity.Field;
import com.unique.examine.module.base.entity.Model;
import com.unique.examine.module.base.entity.Page;
import com.unique.examine.module.base.entity.Record;
import com.unique.examine.module.manage.vo.ModuleManageVO;

import java.util.Map;

/**
 * 动态模块实体转换器。
 */
public final class ModuleManageConverter {

    private ModuleManageConverter() {
    }

    /**
     * 转换模块模型。
     *
     * @param entity 模型实体
     * @return 模块出参
     */
    public static ModuleManageVO fromModel(Model entity) {
        ModuleManageVO vo = base(entity.getId(), entity.getTenantId(), entity.getSystemId(), entity.getAppId(), entity.getStatus());
        vo.setModuleId(entity.getId());
        vo.setCode(entity.getModuleCode());
        vo.setName(entity.getModuleName());
        vo.setType(entity.getDataScopeType());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 转换字段。
     *
     * @param entity 字段实体
     * @return 模块出参
     */
    public static ModuleManageVO fromField(Field entity) {
        ModuleManageVO vo = new ModuleManageVO();
        vo.setId(entity.getId());
        vo.setModuleId(entity.getModuleId());
        vo.setCode(entity.getFieldCode());
        vo.setName(entity.getFieldName());
        vo.setType(entity.getFieldType());
        vo.setConfigJson(entity.getValidationJson());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 转换页面。
     *
     * @param entity 页面实体
     * @return 模块出参
     */
    public static ModuleManageVO fromPage(Page entity) {
        ModuleManageVO vo = new ModuleManageVO();
        vo.setId(entity.getId());
        vo.setModuleId(entity.getModuleId());
        vo.setCode(entity.getPageCode());
        vo.setName(entity.getPageName());
        vo.setType(entity.getPageType());
        vo.setStatus(entity.getStatus());
        vo.setConfigJson(entity.getLayoutJson());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 转换业务记录。
     *
     * @param entity 记录实体
     * @param values 字段值
     * @return 模块出参
     */
    public static ModuleManageVO fromRecord(Record entity, Map<String, Object> values) {
        ModuleManageVO vo = base(entity.getId(), entity.getTenantId(), entity.getSystemId(), entity.getAppId(), entity.getRecordStatus());
        vo.setModuleId(entity.getModuleId());
        vo.setCode(entity.getRecordNo());
        vo.setName(entity.getRecordNo());
        vo.setValues(values);
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 转换导出任务。
     *
     * @param entity 导出任务实体
     * @return 模块出参
     */
    public static ModuleManageVO fromExportJob(ExportJob entity) {
        ModuleManageVO vo = new ModuleManageVO();
        vo.setId(entity.getId());
        vo.setTenantId(entity.getTenantId());
        vo.setModuleId(entity.getModuleId());
        vo.setType(entity.getJobType());
        vo.setStatus(entity.getStatus());
        vo.setConfigJson(entity.getRequestJson());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private static ModuleManageVO base(Long id, Long tenantId, Long systemId, Long appId, String status) {
        ModuleManageVO vo = new ModuleManageVO();
        vo.setId(id);
        vo.setTenantId(tenantId);
        vo.setSystemId(systemId);
        vo.setAppId(appId);
        vo.setStatus(status);
        return vo;
    }
}
