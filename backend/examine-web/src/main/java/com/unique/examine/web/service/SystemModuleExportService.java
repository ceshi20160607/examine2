package com.unique.examine.web.service;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.module.entity.po.ModuleExportTpl;
import com.unique.examine.module.entity.po.ModuleExportTplField;
import com.unique.examine.module.service.IModuleExportTplFieldService;
import com.unique.examine.module.service.IModuleExportTplService;
import com.unique.examine.web.controller.SystemModuleExportController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class SystemModuleExportService {

    @Autowired
    private IModuleExportTplService moduleExportTplService;
    @Autowired
    private IModuleExportTplFieldService moduleExportTplFieldService;

    public List<ModuleExportTpl> listTpls(Long modelId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (modelId == null) {
            throw new BusinessException(400, "modelId 不能为空");
        }
        return moduleExportTplService.lambdaQuery()
                .eq(ModuleExportTpl::getSystemId, systemId)
                .eq(ModuleExportTpl::getTenantId, tenantId)
                .eq(ModuleExportTpl::getModelId, modelId)
                .orderByAsc(ModuleExportTpl::getMenuId)
                .orderByAsc(ModuleExportTpl::getTplCode)
                .list();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModuleExportTpl upsertTpl(Long operatorPlatId, SystemModuleExportController.UpsertTplBody body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.appId() == null || body.modelId() == null) {
            throw new BusinessException(400, "appId/modelId 不能为空");
        }
        if (body.tplCode() == null || body.tplCode().isBlank()) {
            throw new BusinessException(400, "tplCode 不能为空");
        }
        if (body.tplName() == null || body.tplName().isBlank()) {
            throw new BusinessException(400, "tplName 不能为空");
        }
        String fileType = normalizeFileType(body.fileType());
        int status = body.status() == null ? 1 : body.status();
        if (status != 1 && status != 2) {
            throw new BusinessException(400, "status 须为 1=启用 或 2=停用");
        }

        ModuleExportTpl tpl;
        if (body.id() != null) {
            tpl = moduleExportTplService.getById(body.id());
            if (tpl == null) {
                throw new BusinessException(404, "tpl 不存在");
            }
            if (!Objects.equals(tpl.getSystemId(), systemId) || !Objects.equals(tpl.getTenantId(), tenantId)) {
                throw new BusinessException(403, "无权操作该 tpl");
            }
        } else {
            long existed = moduleExportTplService.lambdaQuery()
                    .eq(ModuleExportTpl::getSystemId, systemId)
                    .eq(ModuleExportTpl::getTenantId, tenantId)
                    .eq(ModuleExportTpl::getModelId, body.modelId())
                    .eq(ModuleExportTpl::getMenuId, body.menuId())
                    .eq(ModuleExportTpl::getTplCode, body.tplCode().trim())
                    .count();
            if (existed > 0) {
                throw new BusinessException(400, "tplCode 已存在");
            }
            tpl = new ModuleExportTpl();
            tpl.setSystemId(systemId);
            tpl.setTenantId(tenantId);
            tpl.setCreateUserId(operatorPlatId);
        }

        tpl.setAppId(body.appId());
        tpl.setModelId(body.modelId());
        tpl.setMenuId(body.menuId());
        tpl.setTplCode(body.tplCode().trim());
        tpl.setTplName(body.tplName().trim());
        tpl.setFileType(fileType);
        tpl.setStatus(status);
        tpl.setUpdateUserId(operatorPlatId);

        if (body.id() != null) {
            moduleExportTplService.updateById(tpl);
        } else {
            moduleExportTplService.save(tpl);
        }
        return tpl;
    }

    public List<ModuleExportTplField> listFields(Long tplId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (tplId == null) {
            throw new BusinessException(400, "tplId 不能为空");
        }
        ModuleExportTpl tpl = moduleExportTplService.getById(tplId);
        if (tpl == null) {
            throw new BusinessException(404, "tpl 不存在");
        }
        if (!Objects.equals(tpl.getSystemId(), systemId) || !Objects.equals(tpl.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权访问该 tpl");
        }
        return moduleExportTplFieldService.lambdaQuery()
                .eq(ModuleExportTplField::getSystemId, systemId)
                .eq(ModuleExportTplField::getTenantId, tenantId)
                .eq(ModuleExportTplField::getTplId, tplId)
                .orderByAsc(ModuleExportTplField::getSortNo)
                .orderByAsc(ModuleExportTplField::getId)
                .list();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModuleExportTplField upsertField(Long operatorPlatId, SystemModuleExportController.UpsertFieldBody body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.tplId() == null || body.fieldId() == null) {
            throw new BusinessException(400, "tplId/fieldId 不能为空");
        }
        ModuleExportTpl tpl = moduleExportTplService.getById(body.tplId());
        if (tpl == null) {
            throw new BusinessException(404, "tpl 不存在");
        }
        if (!Objects.equals(tpl.getSystemId(), systemId) || !Objects.equals(tpl.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权操作该 tpl");
        }

        ModuleExportTplField f;
        if (body.id() != null) {
            f = moduleExportTplFieldService.getById(body.id());
            if (f == null) {
                throw new BusinessException(404, "field 不存在");
            }
            if (!Objects.equals(f.getSystemId(), systemId) || !Objects.equals(f.getTenantId(), tenantId) || !Objects.equals(f.getTplId(), body.tplId())) {
                throw new BusinessException(403, "无权操作该导出字段");
            }
        } else {
            f = new ModuleExportTplField();
            f.setSystemId(systemId);
            f.setTenantId(tenantId);
            f.setCreateUserId(operatorPlatId);
        }

        f.setAppId(tpl.getAppId());
        f.setModelId(tpl.getModelId());
        f.setTplId(tpl.getId());
        f.setFieldId(body.fieldId());
        f.setColTitle(trimToNull(body.colTitle()));
        f.setSortNo(body.sortNo());
        f.setFormatJson(trimToNull(body.formatJson()));
        f.setUpdateUserId(operatorPlatId);

        if (body.id() != null) {
            moduleExportTplFieldService.updateById(f);
        } else {
            moduleExportTplFieldService.save(f);
        }
        return f;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTpls(Long operatorPlatId, List<Long> ids) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        moduleExportTplService.lambdaUpdate()
                .eq(ModuleExportTpl::getSystemId, systemId)
                .eq(ModuleExportTpl::getTenantId, tenantId)
                .in(ModuleExportTpl::getId, ids)
                .remove();
        moduleExportTplFieldService.lambdaUpdate()
                .eq(ModuleExportTplField::getSystemId, systemId)
                .eq(ModuleExportTplField::getTenantId, tenantId)
                .in(ModuleExportTplField::getTplId, ids)
                .remove();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteFields(Long operatorPlatId, List<Long> ids) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        moduleExportTplFieldService.lambdaUpdate()
                .eq(ModuleExportTplField::getSystemId, systemId)
                .eq(ModuleExportTplField::getTenantId, tenantId)
                .in(ModuleExportTplField::getId, ids)
                .remove();
    }

    private static String normalizeFileType(String fileType) {
        String t = fileType == null ? "xlsx" : fileType.trim().toLowerCase(Locale.ROOT);
        if (!"xlsx".equals(t) && !"csv".equals(t)) {
            throw new BusinessException(400, "fileType 须为 xlsx|csv");
        }
        return t;
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

