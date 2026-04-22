package com.unique.examine.web.service;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.module.entity.po.ModuleDict;
import com.unique.examine.module.entity.po.ModuleDictItem;
import com.unique.examine.module.service.IModuleDictItemService;
import com.unique.examine.module.service.IModuleDictService;
import com.unique.examine.web.controller.SystemModuleDictController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class SystemModuleDictService {

    @Autowired
    private IModuleDictService moduleDictService;
    @Autowired
    private IModuleDictItemService moduleDictItemService;

    public List<ModuleDict> listDicts(Long appId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (appId == null) {
            throw new BusinessException(400, "appId 不能为空");
        }
        return moduleDictService.lambdaQuery()
                .eq(ModuleDict::getSystemId, systemId)
                .eq(ModuleDict::getTenantId, tenantId)
                .eq(ModuleDict::getAppId, appId)
                .orderByAsc(ModuleDict::getDictCode)
                .list();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModuleDict upsertDict(Long appId, Long operatorPlatId, SystemModuleDictController.UpsertDictBody body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (appId == null) {
            throw new BusinessException(400, "appId 不能为空");
        }
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.dictCode() == null || body.dictCode().isBlank()) {
            throw new BusinessException(400, "dictCode 不能为空");
        }
        if (body.dictName() == null || body.dictName().isBlank()) {
            throw new BusinessException(400, "dictName 不能为空");
        }
        int status = body.status() == null ? 1 : body.status();
        if (status != 1 && status != 2) {
            throw new BusinessException(400, "status 须为 1=启用 或 2=停用");
        }
        String dictCode = body.dictCode().trim();
        String dictName = body.dictName().trim();

        ModuleDict dict;
        if (body.id() != null) {
            dict = moduleDictService.getById(body.id());
            if (dict == null) {
                throw new BusinessException(404, "dict 不存在");
            }
            if (!Objects.equals(dict.getSystemId(), systemId) || !Objects.equals(dict.getTenantId(), tenantId) || !Objects.equals(dict.getAppId(), appId)) {
                throw new BusinessException(403, "无权操作该字典");
            }
            dict.setDictCode(dictCode);
            dict.setDictName(dictName);
            dict.setStatus(status);
            dict.setRemark(trimToNull(body.remark()));
            dict.setUpdateUserId(operatorPlatId);
            moduleDictService.updateById(dict);
        } else {
            long existed = moduleDictService.lambdaQuery()
                    .eq(ModuleDict::getSystemId, systemId)
                    .eq(ModuleDict::getTenantId, tenantId)
                    .eq(ModuleDict::getAppId, appId)
                    .eq(ModuleDict::getDictCode, dictCode)
                    .count();
            if (existed > 0) {
                throw new BusinessException(400, "dictCode 已存在");
            }
            dict = new ModuleDict();
            dict.setSystemId(systemId);
            dict.setTenantId(tenantId);
            dict.setAppId(appId);
            dict.setDictCode(dictCode);
            dict.setDictName(dictName);
            dict.setStatus(status);
            dict.setRemark(trimToNull(body.remark()));
            dict.setCreateUserId(operatorPlatId);
            dict.setUpdateUserId(operatorPlatId);
            moduleDictService.save(dict);
        }
        return dict;
    }

    public List<ModuleDictItem> listItems(Long dictId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (dictId == null) {
            throw new BusinessException(400, "dictId 不能为空");
        }
        ModuleDict dict = moduleDictService.getById(dictId);
        if (dict == null) {
            throw new BusinessException(404, "dict 不存在");
        }
        if (!Objects.equals(dict.getSystemId(), systemId) || !Objects.equals(dict.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权访问该字典");
        }
        return moduleDictItemService.lambdaQuery()
                .eq(ModuleDictItem::getSystemId, systemId)
                .eq(ModuleDictItem::getTenantId, tenantId)
                .eq(ModuleDictItem::getDictId, dictId)
                .orderByAsc(ModuleDictItem::getSortNo)
                .orderByAsc(ModuleDictItem::getId)
                .list();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModuleDictItem upsertItem(Long dictId, Long operatorPlatId, SystemModuleDictController.UpsertItemBody body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (dictId == null) {
            throw new BusinessException(400, "dictId 不能为空");
        }
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.itemValue() == null || body.itemValue().isBlank()) {
            throw new BusinessException(400, "itemValue 不能为空");
        }
        if (body.itemLabel() == null || body.itemLabel().isBlank()) {
            throw new BusinessException(400, "itemLabel 不能为空");
        }
        int status = body.status() == null ? 1 : body.status();
        if (status != 1 && status != 2) {
            throw new BusinessException(400, "status 须为 1=启用 或 2=停用");
        }
        ModuleDict dict = moduleDictService.getById(dictId);
        if (dict == null) {
            throw new BusinessException(404, "dict 不存在");
        }
        if (!Objects.equals(dict.getSystemId(), systemId) || !Objects.equals(dict.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权操作该字典");
        }

        ModuleDictItem item;
        if (body.id() != null) {
            item = moduleDictItemService.getById(body.id());
            if (item == null) {
                throw new BusinessException(404, "item 不存在");
            }
            if (!Objects.equals(item.getSystemId(), systemId) || !Objects.equals(item.getTenantId(), tenantId) || !Objects.equals(item.getDictId(), dictId)) {
                throw new BusinessException(403, "无权操作该字典项");
            }
            item.setItemValue(body.itemValue().trim());
            item.setItemLabel(body.itemLabel().trim());
            item.setSortNo(body.sortNo());
            item.setStatus(status);
            item.setUpdateUserId(operatorPlatId);
            moduleDictItemService.updateById(item);
        } else {
            item = new ModuleDictItem();
            item.setSystemId(systemId);
            item.setTenantId(tenantId);
            item.setAppId(dict.getAppId());
            item.setDictId(dictId);
            item.setItemValue(body.itemValue().trim());
            item.setItemLabel(body.itemLabel().trim());
            item.setSortNo(body.sortNo());
            item.setStatus(status);
            item.setCreateUserId(operatorPlatId);
            item.setUpdateUserId(operatorPlatId);
            moduleDictItemService.save(item);
        }
        return item;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDicts(Long operatorPlatId, List<Long> ids) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        moduleDictService.lambdaUpdate()
                .eq(ModuleDict::getSystemId, systemId)
                .eq(ModuleDict::getTenantId, tenantId)
                .in(ModuleDict::getId, ids)
                .remove();
        moduleDictItemService.lambdaUpdate()
                .eq(ModuleDictItem::getSystemId, systemId)
                .eq(ModuleDictItem::getTenantId, tenantId)
                .in(ModuleDictItem::getDictId, ids)
                .remove();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteItems(Long operatorPlatId, List<Long> ids) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        moduleDictItemService.lambdaUpdate()
                .eq(ModuleDictItem::getSystemId, systemId)
                .eq(ModuleDictItem::getTenantId, tenantId)
                .in(ModuleDictItem::getId, ids)
                .remove();
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

