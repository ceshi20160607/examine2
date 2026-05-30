package com.unique.examine.module.manage;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.module.entity.po.ModuleField;
import com.unique.examine.module.entity.po.ModuleListFilterField;
import com.unique.examine.module.entity.po.ModuleListFilterTpl;
import com.unique.examine.module.entity.po.ModuleListView;
import com.unique.examine.module.entity.po.ModuleListViewCol;
import com.unique.examine.module.service.IModuleFieldService;
import com.unique.examine.module.service.IModuleListFilterFieldService;
import com.unique.examine.module.service.IModuleListFilterTplService;
import com.unique.examine.module.service.IModuleListViewColService;
import com.unique.examine.module.service.IModuleListViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class SystemModuleListViewService {

    public record UpsertViewCmd(Long id, Long appId, Long modelId, Long platId, String viewCode, String viewName, Integer defaultFlag, Integer status) {}
    public record UpsertColCmd(Long id, Long viewId, Long fieldId, String colTitle, Integer width, Integer sortNo, Integer visibleFlag, String fixedType, String formatJson) {}
    public record UpsertFilterTplCmd(Long id, Long appId, Long modelId, Long menuId, String tplCode, String tplName, Integer status) {}
    public record UpsertFilterFieldCmd(Long id, Long tplId, Long fieldId, String opCode, String defaultValue, Integer requiredFlag, Integer sortNo) {}

    @Autowired
    private IModuleListViewService moduleListViewService;
    @Autowired
    private IModuleListViewColService moduleListViewColService;
    @Autowired
    private IModuleListFilterTplService moduleListFilterTplService;
    @Autowired
    private IModuleListFilterFieldService moduleListFilterFieldService;
    @Autowired
    private IModuleFieldService moduleFieldService;

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
    public ModuleListView upsertView(Long operatorPlatId, UpsertViewCmd body) {
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
        Long platId = normalizeNullableId(body.platId());
        String viewCode = body.viewCode().trim();

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
            view = new ModuleListView();
            view.setSystemId(systemId);
            view.setTenantId(tenantId);
            view.setCreateUserId(operatorPlatId);
        }
        if (countSameViewCode(systemId, tenantId, body.modelId(), platId, viewCode, view.getId()) > 0) {
            throw new BusinessException(400, "viewCode 已存在");
        }

        view.setAppId(body.appId());
        view.setModelId(body.modelId());
        view.setPlatId(platId);
        view.setViewCode(viewCode);
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
    public ModuleListViewCol upsertCol(Long operatorPlatId, UpsertColCmd body) {
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
        ModuleField field = moduleFieldService.getById(body.fieldId());
        if (!isFieldInModel(field, systemId, tenantId, view.getAppId(), view.getModelId())) {
            throw new BusinessException(400, "field 不属于该列表视图模型");
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
        if (countSameViewCol(systemId, tenantId, view.getId(), field.getId(), col.getId()) > 0) {
            throw new BusinessException(400, "该字段已在列表视图中");
        }

        col.setAppId(view.getAppId());
        col.setModelId(view.getModelId());
        col.setViewId(view.getId());
        col.setFieldId(field.getId());
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
    public ModuleListFilterTpl upsertFilterTpl(Long operatorPlatId, UpsertFilterTplCmd body) {
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
        Long menuId = normalizeNullableId(body.menuId());
        String tplCode = body.tplCode().trim();
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
            tpl = new ModuleListFilterTpl();
            tpl.setSystemId(systemId);
            tpl.setTenantId(tenantId);
            tpl.setCreateUserId(operatorPlatId);
        }
        if (countSameFilterTplCode(systemId, tenantId, body.modelId(), menuId, tplCode, tpl.getId()) > 0) {
            throw new BusinessException(400, "tplCode 已存在");
        }

        tpl.setAppId(body.appId());
        tpl.setModelId(body.modelId());
        tpl.setMenuId(menuId);
        tpl.setTplCode(tplCode);
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
        moduleListFilterFieldService.lambdaUpdate()
                .eq(ModuleListFilterField::getSystemId, systemId)
                .eq(ModuleListFilterField::getTenantId, tenantId)
                .in(ModuleListFilterField::getTplId, ids)
                .remove();
    }

    public List<ModuleListFilterField> listFilterFields(Long tplId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        ModuleListFilterTpl tpl = requireFilterTpl(tplId, systemId, tenantId);
        return moduleListFilterFieldService.lambdaQuery()
                .eq(ModuleListFilterField::getSystemId, systemId)
                .eq(ModuleListFilterField::getTenantId, tenantId)
                .eq(ModuleListFilterField::getTplId, tpl.getId())
                .orderByAsc(ModuleListFilterField::getSortNo)
                .orderByAsc(ModuleListFilterField::getId)
                .list();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModuleListFilterField upsertFilterField(Long operatorPlatId, UpsertFilterFieldCmd body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.tplId() == null || body.fieldId() == null) {
            throw new BusinessException(400, "tplId/fieldId 不能为空");
        }
        ModuleListFilterTpl tpl = requireFilterTpl(body.tplId(), systemId, tenantId);
        ModuleField field = moduleFieldService.getById(body.fieldId());
        if (field == null
                || !Objects.equals(field.getSystemId(), systemId)
                || !Objects.equals(field.getTenantId(), tenantId)
                || !Objects.equals(field.getAppId(), tpl.getAppId())
                || !Objects.equals(field.getModelId(), tpl.getModelId())) {
            throw new BusinessException(400, "field 不属于该筛选模板模型");
        }
        String op = normalizeFilterOp(body.opCode());
        int required = body.requiredFlag() == null ? 0 : body.requiredFlag();
        if (required != 0 && required != 1) {
            throw new BusinessException(400, "requiredFlag 须为 0/1");
        }

        ModuleListFilterField item;
        if (body.id() != null) {
            item = moduleListFilterFieldService.getById(body.id());
            if (item == null) {
                throw new BusinessException(404, "filter field 不存在");
            }
            if (!Objects.equals(item.getSystemId(), systemId)
                    || !Objects.equals(item.getTenantId(), tenantId)
                    || !Objects.equals(item.getTplId(), tpl.getId())) {
                throw new BusinessException(403, "无权操作该筛选项");
            }
        } else {
            item = new ModuleListFilterField();
            item.setSystemId(systemId);
            item.setTenantId(tenantId);
            item.setCreateUserId(operatorPlatId);
        }

        item.setAppId(tpl.getAppId());
        item.setModelId(tpl.getModelId());
        item.setTplId(tpl.getId());
        item.setFieldId(body.fieldId());
        item.setOpCode(op);
        item.setDefaultValue(trimToNull(body.defaultValue()));
        item.setRequiredFlag(required);
        item.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
        item.setUpdateUserId(operatorPlatId);

        if (body.id() != null) {
            moduleListFilterFieldService.updateById(item);
        } else {
            moduleListFilterFieldService.save(item);
        }
        return item;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteFilterFields(Long operatorPlatId, List<Long> ids) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        moduleListFilterFieldService.lambdaUpdate()
                .eq(ModuleListFilterField::getSystemId, systemId)
                .eq(ModuleListFilterField::getTenantId, tenantId)
                .in(ModuleListFilterField::getId, ids)
                .remove();
    }

    private ModuleListFilterTpl requireFilterTpl(Long tplId, long systemId, long tenantId) {
        if (tplId == null) {
            throw new BusinessException(400, "tplId 不能为空");
        }
        ModuleListFilterTpl tpl = moduleListFilterTplService.getById(tplId);
        if (tpl == null) {
            throw new BusinessException(404, "filter tpl 不存在");
        }
        if (!Objects.equals(tpl.getSystemId(), systemId) || !Objects.equals(tpl.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权访问该筛选模板");
        }
        return tpl;
    }

    private long countSameViewCode(long systemId, long tenantId, Long modelId, Long platId, String viewCode, Long excludeId) {
        var q = moduleListViewService.lambdaQuery()
                .eq(ModuleListView::getSystemId, systemId)
                .eq(ModuleListView::getTenantId, tenantId)
                .eq(ModuleListView::getModelId, modelId)
                .eq(ModuleListView::getViewCode, viewCode);
        if (platId == null) {
            q.isNull(ModuleListView::getPlatId);
        } else {
            q.eq(ModuleListView::getPlatId, platId);
        }
        if (excludeId != null) {
            q.ne(ModuleListView::getId, excludeId);
        }
        return q.count();
    }

    private long countSameFilterTplCode(long systemId, long tenantId, Long modelId, Long menuId, String tplCode, Long excludeId) {
        var q = moduleListFilterTplService.lambdaQuery()
                .eq(ModuleListFilterTpl::getSystemId, systemId)
                .eq(ModuleListFilterTpl::getTenantId, tenantId)
                .eq(ModuleListFilterTpl::getModelId, modelId)
                .eq(ModuleListFilterTpl::getTplCode, tplCode);
        if (menuId == null) {
            q.isNull(ModuleListFilterTpl::getMenuId);
        } else {
            q.eq(ModuleListFilterTpl::getMenuId, menuId);
        }
        if (excludeId != null) {
            q.ne(ModuleListFilterTpl::getId, excludeId);
        }
        return q.count();
    }

    private long countSameViewCol(long systemId, long tenantId, Long viewId, Long fieldId, Long excludeId) {
        var q = moduleListViewColService.lambdaQuery()
                .eq(ModuleListViewCol::getSystemId, systemId)
                .eq(ModuleListViewCol::getTenantId, tenantId)
                .eq(ModuleListViewCol::getViewId, viewId)
                .eq(ModuleListViewCol::getFieldId, fieldId);
        if (excludeId != null) {
            q.ne(ModuleListViewCol::getId, excludeId);
        }
        return q.count();
    }

    private static boolean isFieldInModel(ModuleField field, long systemId, long tenantId, Long appId, Long modelId) {
        return field != null
                && Objects.equals(field.getSystemId(), systemId)
                && Objects.equals(field.getTenantId(), tenantId)
                && Objects.equals(field.getAppId(), appId)
                && Objects.equals(field.getModelId(), modelId)
                && Objects.equals(field.getStatus(), 1);
    }

    private static String normalizeFilterOp(String opCode) {
        String op = trimToNull(opCode);
        if (op == null) {
            op = "eq";
        }
        op = op.toLowerCase();
        if (!List.of("eq", "ne", "like", "in", "between", "gt", "ge", "lt", "le").contains(op)) {
            throw new BusinessException(400, "opCode 不支持: " + op);
        }
        return op;
    }

    private static Long normalizeNullableId(Long id) {
        return id == null || id <= 0L ? null : id;
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

