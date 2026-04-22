package com.unique.examine.module.manage;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.module.entity.po.ModuleAction;
import com.unique.examine.module.entity.po.ModuleApp;
import com.unique.examine.module.entity.po.ModuleField;
import com.unique.examine.module.entity.po.ModuleModel;
import com.unique.examine.module.entity.po.ModuleRelation;
import com.unique.examine.module.service.IModuleActionService;
import com.unique.examine.module.service.IModuleAppService;
import com.unique.examine.module.service.IModuleFieldService;
import com.unique.examine.module.service.IModuleModelService;
import com.unique.examine.module.service.IModuleRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class SystemModuleMetaService {

    public record UpsertAppCmd(
            Long id,
            String appCode,
            String appName,
            String iconUrl,
            Integer status,
            Integer publishedFlag,
            String remark
    ) {}

    public record UpsertModelCmd(
            Long id,
            Long appId,
            String modelCode,
            String modelName,
            Integer status,
            String remark
    ) {}

    public record UpsertFieldCmd(
            Long id,
            Long appId,
            Long modelId,
            String fieldCode,
            String fieldName,
            String fieldType,
            Integer requiredFlag,
            Integer uniqueFlag,
            Integer hiddenFlag,
            String tips,
            Integer maxLength,
            Integer minLength,
            String validateType,
            String dateFormat,
            String dictCode,
            Integer multiFlag,
            String defaultValue,
            Integer sortNo,
            Integer status
    ) {}

    public record UpsertRelationCmd(
            Long id,
            Long appId,
            Long srcModelId,
            Long dstModelId,
            String relType,
            String configJson
    ) {}

    @Autowired
    private IModuleAppService moduleAppService;
    @Autowired
    private IModuleModelService moduleModelService;
    @Autowired
    private IModuleFieldService moduleFieldService;
    @Autowired
    private IModuleRelationService moduleRelationService;
    @Autowired
    private IModuleActionService moduleActionService;

    public List<ModuleApp> listApps(Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        return moduleAppService.lambdaQuery()
                .eq(ModuleApp::getSystemId, systemId)
                .eq(ModuleApp::getTenantId, tenantId)
                .orderByAsc(ModuleApp::getAppCode)
                .list();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModuleApp upsertApp(Long operatorPlatId, UpsertAppCmd body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.appCode() == null || body.appCode().isBlank()) {
            throw new BusinessException(400, "appCode 不能为空");
        }
        if (body.appName() == null || body.appName().isBlank()) {
            throw new BusinessException(400, "appName 不能为空");
        }
        int status = body.status() == null ? 1 : body.status();
        if (status != 1 && status != 2) {
            throw new BusinessException(400, "status 须为 1=启用 或 2=停用");
        }
        int published = body.publishedFlag() == null ? 0 : body.publishedFlag();
        if (published != 0 && published != 1) {
            throw new BusinessException(400, "publishedFlag 须为 0/1");
        }
        String appCode = body.appCode().trim();
        String appName = body.appName().trim();

        ModuleApp app;
        if (body.id() != null) {
            app = moduleAppService.getById(body.id());
            if (app == null) {
                throw new BusinessException(404, "app 不存在");
            }
            if (!Objects.equals(app.getSystemId(), systemId) || !Objects.equals(app.getTenantId(), tenantId)) {
                throw new BusinessException(403, "无权操作该 app");
            }
            app.setAppCode(appCode);
            app.setAppName(appName);
            app.setIconUrl(trimToNull(body.iconUrl()));
            app.setStatus(status);
            app.setPublishedFlag(published);
            app.setRemark(trimToNull(body.remark()));
            app.setUpdateUserId(operatorPlatId);
            moduleAppService.updateById(app);
        } else {
            long existed = moduleAppService.lambdaQuery()
                    .eq(ModuleApp::getSystemId, systemId)
                    .eq(ModuleApp::getTenantId, tenantId)
                    .eq(ModuleApp::getAppCode, appCode)
                    .count();
            if (existed > 0) {
                throw new BusinessException(400, "appCode 已存在");
            }
            app = new ModuleApp();
            app.setSystemId(systemId);
            app.setTenantId(tenantId);
            app.setAppCode(appCode);
            app.setAppName(appName);
            app.setIconUrl(trimToNull(body.iconUrl()));
            app.setStatus(status);
            app.setPublishedFlag(published);
            app.setRemark(trimToNull(body.remark()));
            app.setCreateUserId(operatorPlatId);
            app.setUpdateUserId(operatorPlatId);
            moduleAppService.save(app);
        }
        return app;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteApps(Long operatorPlatId, List<Long> ids) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        moduleAppService.lambdaUpdate()
                .eq(ModuleApp::getSystemId, systemId)
                .eq(ModuleApp::getTenantId, tenantId)
                .in(ModuleApp::getId, ids)
                .remove();
    }

    public List<ModuleModel> listModels(Long appId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (appId == null) {
            throw new BusinessException(400, "appId 不能为空");
        }
        return moduleModelService.lambdaQuery()
                .eq(ModuleModel::getSystemId, systemId)
                .eq(ModuleModel::getTenantId, tenantId)
                .eq(ModuleModel::getAppId, appId)
                .orderByAsc(ModuleModel::getModelCode)
                .list();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModuleModel upsertModel(Long operatorPlatId, UpsertModelCmd body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.appId() == null) {
            throw new BusinessException(400, "appId 不能为空");
        }
        if (body.modelCode() == null || body.modelCode().isBlank()) {
            throw new BusinessException(400, "modelCode 不能为空");
        }
        if (body.modelName() == null || body.modelName().isBlank()) {
            throw new BusinessException(400, "modelName 不能为空");
        }
        int status = body.status() == null ? 1 : body.status();
        if (status != 1 && status != 2) {
            throw new BusinessException(400, "status 须为 1=启用 或 2=停用");
        }
        String modelCode = body.modelCode().trim();
        String modelName = body.modelName().trim();

        ModuleModel m;
        if (body.id() != null) {
            m = moduleModelService.getById(body.id());
            if (m == null) {
                throw new BusinessException(404, "model 不存在");
            }
            if (!Objects.equals(m.getSystemId(), systemId) || !Objects.equals(m.getTenantId(), tenantId)) {
                throw new BusinessException(403, "无权操作该 model");
            }
            m.setAppId(body.appId());
            m.setModelCode(modelCode);
            m.setModelName(modelName);
            m.setStatus(status);
            m.setRemark(trimToNull(body.remark()));
            m.setUpdateUserId(operatorPlatId);
            moduleModelService.updateById(m);
        } else {
            long existed = moduleModelService.lambdaQuery()
                    .eq(ModuleModel::getSystemId, systemId)
                    .eq(ModuleModel::getTenantId, tenantId)
                    .eq(ModuleModel::getAppId, body.appId())
                    .eq(ModuleModel::getModelCode, modelCode)
                    .count();
            if (existed > 0) {
                throw new BusinessException(400, "modelCode 已存在");
            }
            m = new ModuleModel();
            m.setSystemId(systemId);
            m.setTenantId(tenantId);
            m.setAppId(body.appId());
            m.setModelCode(modelCode);
            m.setModelName(modelName);
            m.setStatus(status);
            m.setRemark(trimToNull(body.remark()));
            m.setCreateUserId(operatorPlatId);
            m.setUpdateUserId(operatorPlatId);
            moduleModelService.save(m);
        }
        return m;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteModels(Long operatorPlatId, List<Long> ids) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        moduleModelService.lambdaUpdate()
                .eq(ModuleModel::getSystemId, systemId)
                .eq(ModuleModel::getTenantId, tenantId)
                .in(ModuleModel::getId, ids)
                .remove();
    }

    public List<ModuleField> listFields(Long modelId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (modelId == null) {
            throw new BusinessException(400, "modelId 不能为空");
        }
        return moduleFieldService.lambdaQuery()
                .eq(ModuleField::getSystemId, systemId)
                .eq(ModuleField::getTenantId, tenantId)
                .eq(ModuleField::getModelId, modelId)
                .orderByAsc(ModuleField::getSortNo)
                .orderByAsc(ModuleField::getFieldCode)
                .list();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModuleField upsertField(Long operatorPlatId, UpsertFieldCmd body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.appId() == null || body.modelId() == null) {
            throw new BusinessException(400, "appId/modelId 不能为空");
        }
        if (body.fieldCode() == null || body.fieldCode().isBlank()) {
            throw new BusinessException(400, "fieldCode 不能为空");
        }
        if (body.fieldName() == null || body.fieldName().isBlank()) {
            throw new BusinessException(400, "fieldName 不能为空");
        }
        if (body.fieldType() == null || body.fieldType().isBlank()) {
            throw new BusinessException(400, "fieldType 不能为空");
        }
        int status = body.status() == null ? 1 : body.status();
        if (status != 1 && status != 2) {
            throw new BusinessException(400, "status 须为 1=启用 或 2=停用");
        }

        ModuleField f;
        if (body.id() != null) {
            f = moduleFieldService.getById(body.id());
            if (f == null) {
                throw new BusinessException(404, "field 不存在");
            }
            if (!Objects.equals(f.getSystemId(), systemId) || !Objects.equals(f.getTenantId(), tenantId)) {
                throw new BusinessException(403, "无权操作该 field");
            }
        } else {
            long existed = moduleFieldService.lambdaQuery()
                    .eq(ModuleField::getSystemId, systemId)
                    .eq(ModuleField::getTenantId, tenantId)
                    .eq(ModuleField::getModelId, body.modelId())
                    .eq(ModuleField::getFieldCode, body.fieldCode().trim())
                    .count();
            if (existed > 0) {
                throw new BusinessException(400, "fieldCode 已存在");
            }
            f = new ModuleField();
            f.setSystemId(systemId);
            f.setTenantId(tenantId);
            f.setCreateUserId(operatorPlatId);
        }

        f.setAppId(body.appId());
        f.setModelId(body.modelId());
        f.setFieldCode(body.fieldCode().trim());
        f.setFieldName(body.fieldName().trim());
        f.setFieldType(body.fieldType().trim());
        f.setRequiredFlag(body.requiredFlag());
        f.setUniqueFlag(body.uniqueFlag());
        f.setHiddenFlag(body.hiddenFlag());
        f.setTips(trimToNull(body.tips()));
        f.setMaxLength(body.maxLength());
        f.setMinLength(body.minLength());
        f.setValidateType(trimToNull(body.validateType()));
        f.setDateFormat(trimToNull(body.dateFormat()));
        f.setDictCode(trimToNull(body.dictCode()));
        f.setMultiFlag(body.multiFlag());
        f.setDefaultValue(trimToNull(body.defaultValue()));
        f.setSortNo(body.sortNo());
        f.setStatus(status);
        f.setUpdateUserId(operatorPlatId);

        if (body.id() != null) {
            moduleFieldService.updateById(f);
        } else {
            moduleFieldService.save(f);
        }
        return f;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteFields(Long operatorPlatId, List<Long> ids) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        moduleFieldService.lambdaUpdate()
                .eq(ModuleField::getSystemId, systemId)
                .eq(ModuleField::getTenantId, tenantId)
                .in(ModuleField::getId, ids)
                .remove();
    }

    public List<ModuleRelation> listRelations(Long appId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (appId == null) {
            throw new BusinessException(400, "appId 不能为空");
        }
        return moduleRelationService.lambdaQuery()
                .eq(ModuleRelation::getSystemId, systemId)
                .eq(ModuleRelation::getTenantId, tenantId)
                .eq(ModuleRelation::getAppId, appId)
                .orderByAsc(ModuleRelation::getId)
                .list();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModuleRelation upsertRelation(Long operatorPlatId, UpsertRelationCmd body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.appId() == null || body.srcModelId() == null || body.dstModelId() == null) {
            throw new BusinessException(400, "appId/srcModelId/dstModelId 不能为空");
        }
        if (body.relType() == null || body.relType().isBlank()) {
            throw new BusinessException(400, "relType 不能为空");
        }
        ModuleRelation r;
        if (body.id() != null) {
            r = moduleRelationService.getById(body.id());
            if (r == null) {
                throw new BusinessException(404, "relation 不存在");
            }
            if (!Objects.equals(r.getSystemId(), systemId) || !Objects.equals(r.getTenantId(), tenantId)) {
                throw new BusinessException(403, "无权操作该 relation");
            }
        } else {
            r = new ModuleRelation();
            r.setSystemId(systemId);
            r.setTenantId(tenantId);
            r.setCreateUserId(operatorPlatId);
        }
        r.setAppId(body.appId());
        r.setSrcModelId(body.srcModelId());
        r.setDstModelId(body.dstModelId());
        r.setRelType(body.relType().trim());
        r.setConfigJson(trimToNull(body.configJson()));
        r.setUpdateUserId(operatorPlatId);
        if (body.id() != null) {
            moduleRelationService.updateById(r);
        } else {
            moduleRelationService.save(r);
        }
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRelations(Long operatorPlatId, List<Long> ids) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        moduleRelationService.lambdaUpdate()
                .eq(ModuleRelation::getSystemId, systemId)
                .eq(ModuleRelation::getTenantId, tenantId)
                .in(ModuleRelation::getId, ids)
                .remove();
    }

    public List<ModuleAction> listActions(Long operatorPlatId) {
        requireOperator(operatorPlatId);
        return moduleActionService.lambdaQuery()
                .orderByAsc(ModuleAction::getActionCode)
                .list();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static void requireOperator(Long platId) {
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
    }

    private static long requireSystem() {
        long sid = AuthContextHolder.getSystemIdOrDefault();
        if (sid == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        return sid;
    }
}

