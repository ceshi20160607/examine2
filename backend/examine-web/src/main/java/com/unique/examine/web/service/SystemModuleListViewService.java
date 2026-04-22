package com.unique.examine.web.service;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.module.entity.po.ModuleListFilterTpl;
import com.unique.examine.module.entity.po.ModuleListView;
import com.unique.examine.module.entity.po.ModuleListViewCol;
import com.unique.examine.module.service.IModuleListFilterTplService;
import com.unique.examine.module.service.IModuleListViewColService;
import com.unique.examine.module.service.IModuleListViewService;
import com.unique.examine.web.controller.SystemModuleListViewController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class SystemModuleListViewService {

    @Autowired
    private IModuleListViewService moduleListViewService;
    @Autowired
    private IModuleListViewColService moduleListViewColService;
    @Autowired
    private IModuleListFilterTplService moduleListFilterTplService;

    public List<ModuleListView> listViews(Long modelId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (modelId == null) {
            throw new BusinessException(400, "modelId 不能为空");
        }
        return moduleListViewService.lambdaQuery()
                .eq(ModuleListView::getSystemId, systemId)
                .eq(ModuleListView::getTenantId, tenantId)
                .eq(ModuleListView::getModelId, modelId)
                .orderByAsc(ModuleListView::getPlatId)
                .orderByAsc(ModuleListView::getViewCode)
                .list();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModuleListView upsertView(Long operatorPlatId, SystemModuleListViewController.UpsertViewBody body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.appId() == null || body.modelId() == null) {
            throw new BusinessException(400, "appId/modelId 不能为空");
        }
        if (body.viewCode() == null || body.viewCode().isBlank()) {
            throw new BusinessException(400, "viewCode 不能为空");
        }
        if (body.viewName() == null || body.viewName().isBlank()) {
            throw new BusinessException(400, "viewName 不能为空");
        }
        int status = body.status() == null ? 1 : body.status();
        if (status != 1 && status != 2) {
            throw new BusinessException(400, "status 须为 1=启用 或 2=停用");
        }
        int defaultFlag = body.defaultFlag() == null ? 0 : body.defaultFlag();
        if (defaultFlag != 0 && defaultFlag != 1) {
            throw new BusinessException(400, "defaultFlag 须为 0/1");
        }

        ModuleListView view;
        if (body.id() != null) {
            view = moduleListViewService.getById(body.id());
            if (view == null) {
                throw new BusinessException(404, "view 不存在");
            }
            if (!Objects.equals(view.getSystemId(), systemId) || !Objects.equals(view.getTenantId(), tenantId)) {
                throw new BusinessException(403, "无权操作该视图");
            }
        } else {
            long existed = moduleListViewService.lambdaQuery()
                    .eq(ModuleListView::getSystemId, systemId)
                    .eq(ModuleListView::getTenantId, tenantId)
                    .eq(ModuleListView::getModelId, body.modelId())
                    .eq(ModuleListView::getPlatId, body.platId())
                    .eq(ModuleListView::getViewCode, body.viewCode().trim())
                    .count();
            if (existed > 0) {
                throw new BusinessException(400, "viewCode 已存在");
            }
            view = new ModuleListView();
            view.setSystemId(systemId);
            view.setTenantId(tenantId);
            view.setCreateUserId(operatorPlatId);
        }

        view.setAppId(body.appId());
        view.setModelId(body.modelId());
        view.setPlatId(body.platId());
        view.setViewCode(body.viewCode().trim());
        view.setViewName(body.viewName().trim());
        view.setDefaultFlag(defaultFlag);
        view.setStatus(status);
        view.setUpdateUserId(operatorPlatId);

        if (body.id() != null) {
            moduleListViewService.updateById(view);
        } else {
            moduleListViewService.save(view);
        }
        return view;
    }

    public List<ModuleListViewCol> listCols(Long viewId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (viewId == null) {
            throw new BusinessException(400, "viewId 不能为空");
        }
        ModuleListView view = moduleListViewService.getById(viewId);
        if (view == null) {
            throw new BusinessException(404, "view 不存在");
        }
        if (!Objects.equals(view.getSystemId(), systemId) || !Objects.equals(view.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权访问该 view");
        }
        return moduleListViewColService.lambdaQuery()
                .eq(ModuleListViewCol::getSystemId, systemId)
                .eq(ModuleListViewCol::getTenantId, tenantId)
                .eq(ModuleListViewCol::getViewId, viewId)
                .orderByAsc(ModuleListViewCol::getSortNo)
                .orderByAsc(ModuleListViewCol::getId)
                .list();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModuleListViewCol upsertCol(Long operatorPlatId, SystemModuleListViewController.UpsertColBody body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.viewId() == null || body.fieldId() == null) {
            throw new BusinessException(400, "viewId/fieldId 不能为空");
        }
        Integer visible = body.visibleFlag() == null ? 1 : body.visibleFlag();
        if (visible != 0 && visible != 1) {
            throw new BusinessException(400, "visibleFlag 须为 0/1");
        }
        ModuleListView view = moduleListViewService.getById(body.viewId());
        if (view == null) {
            throw new BusinessException(404, "view 不存在");
        }
        if (!Objects.equals(view.getSystemId(), systemId) || !Objects.equals(view.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权操作该 view");
        }

        ModuleListViewCol col;
        if (body.id() != null) {
            col = moduleListViewColService.getById(body.id());
            if (col == null) {
                throw new BusinessException(404, "col 不存在");
            }
            if (!Objects.equals(col.getSystemId(), systemId) || !Objects.equals(col.getTenantId(), tenantId) || !Objects.equals(col.getViewId(), body.viewId())) {
                throw new BusinessException(403, "无权操作该 col");
            }
        } else {
            col = new ModuleListViewCol();
            col.setSystemId(systemId);
            col.setTenantId(tenantId);
            col.setCreateUserId(operatorPlatId);
        }

        col.setAppId(view.getAppId());
        col.setModelId(view.getModelId());
        col.setViewId(view.getId());
        col.setFieldId(body.fieldId());
        col.setColTitle(trimToNull(body.colTitle()));
        col.setWidth(body.width());
        col.setSortNo(body.sortNo());
        col.setVisibleFlag(visible);
        col.setFixedType(trimToNull(body.fixedType()));
        col.setFormatJson(trimToNull(body.formatJson()));
        col.setUpdateUserId(operatorPlatId);

        if (body.id() != null) {
            moduleListViewColService.updateById(col);
        } else {
            moduleListViewColService.save(col);
        }
        return col;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteViews(Long operatorPlatId, List<Long> ids) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        moduleListViewService.lambdaUpdate()
                .eq(ModuleListView::getSystemId, systemId)
                .eq(ModuleListView::getTenantId, tenantId)
                .in(ModuleListView::getId, ids)
                .remove();
        moduleListViewColService.lambdaUpdate()
                .eq(ModuleListViewCol::getSystemId, systemId)
                .eq(ModuleListViewCol::getTenantId, tenantId)
                .in(ModuleListViewCol::getViewId, ids)
                .remove();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCols(Long operatorPlatId, List<Long> ids) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        moduleListViewColService.lambdaUpdate()
                .eq(ModuleListViewCol::getSystemId, systemId)
                .eq(ModuleListViewCol::getTenantId, tenantId)
                .in(ModuleListViewCol::getId, ids)
                .remove();
    }

    public List<ModuleListFilterTpl> listFilterTpls(Long modelId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (modelId == null) {
            throw new BusinessException(400, "modelId 不能为空");
        }
        return moduleListFilterTplService.lambdaQuery()
                .eq(ModuleListFilterTpl::getSystemId, systemId)
                .eq(ModuleListFilterTpl::getTenantId, tenantId)
                .eq(ModuleListFilterTpl::getModelId, modelId)
                .orderByAsc(ModuleListFilterTpl::getMenuId)
                .orderByAsc(ModuleListFilterTpl::getTplCode)
                .list();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModuleListFilterTpl upsertFilterTpl(Long operatorPlatId, SystemModuleListViewController.UpsertFilterTplBody body) {
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
        int status = body.status() == null ? 1 : body.status();
        if (status != 1 && status != 2) {
            throw new BusinessException(400, "status 须为 1=启用 或 2=停用");
        }

        ModuleListFilterTpl tpl;
        if (body.id() != null) {
            tpl = moduleListFilterTplService.getById(body.id());
            if (tpl == null) {
                throw new BusinessException(404, "tpl 不存在");
            }
            if (!Objects.equals(tpl.getSystemId(), systemId) || !Objects.equals(tpl.getTenantId(), tenantId)) {
                throw new BusinessException(403, "无权操作该 tpl");
            }
        } else {
            long existed = moduleListFilterTplService.lambdaQuery()
                    .eq(ModuleListFilterTpl::getSystemId, systemId)
                    .eq(ModuleListFilterTpl::getTenantId, tenantId)
                    .eq(ModuleListFilterTpl::getModelId, body.modelId())
                    .eq(ModuleListFilterTpl::getMenuId, body.menuId())
                    .eq(ModuleListFilterTpl::getTplCode, body.tplCode().trim())
                    .count();
            if (existed > 0) {
                throw new BusinessException(400, "tplCode 已存在");
            }
            tpl = new ModuleListFilterTpl();
            tpl.setSystemId(systemId);
            tpl.setTenantId(tenantId);
            tpl.setCreateUserId(operatorPlatId);
        }

        tpl.setAppId(body.appId());
        tpl.setModelId(body.modelId());
        tpl.setMenuId(body.menuId());
        tpl.setTplCode(body.tplCode().trim());
        tpl.setTplName(body.tplName().trim());
        tpl.setStatus(status);
        tpl.setUpdateUserId(operatorPlatId);

        if (body.id() != null) {
            moduleListFilterTplService.updateById(tpl);
        } else {
            moduleListFilterTplService.save(tpl);
        }
        return tpl;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteFilterTpls(Long operatorPlatId, List<Long> ids) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        moduleListFilterTplService.lambdaUpdate()
                .eq(ModuleListFilterTpl::getSystemId, systemId)
                .eq(ModuleListFilterTpl::getTenantId, tenantId)
                .in(ModuleListFilterTpl::getId, ids)
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

