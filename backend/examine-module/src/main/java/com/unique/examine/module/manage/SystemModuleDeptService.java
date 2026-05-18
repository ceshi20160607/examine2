package com.unique.examine.module.manage;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.module.entity.po.ModuleDept;
import com.unique.examine.module.entity.po.ModuleMember;
import com.unique.examine.module.service.IModuleDeptService;
import com.unique.examine.module.service.IModuleMemberService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemModuleDeptService {

    public record UpsertDeptCmd(
            Long id,
            Long parentId,
            String deptCode,
            String deptName,
            Integer sortNo,
            Integer status,
            String remark
    ) {}

    @Autowired
    private IModuleDeptService moduleDeptService;
    @Autowired
    private IModuleMemberService moduleMemberService;

    public List<ModuleDept> listDepts(Long appId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        requireAppId(appId);
        return moduleDeptService.lambdaQuery()
                .eq(ModuleDept::getSystemId, systemId)
                .eq(ModuleDept::getTenantId, tenantId)
                .eq(ModuleDept::getAppId, appId)
                .orderByAsc(ModuleDept::getParentId)
                .orderByAsc(ModuleDept::getSortNo)
                .orderByAsc(ModuleDept::getDeptCode)
                .list();
    }

    public List<Map<String, Object>> listPickerOptions(Long appId, Long operatorPlatId) {
        List<ModuleDept> depts = listDepts(appId, operatorPlatId);
        Map<Long, ModuleDept> byId = new HashMap<>();
        for (ModuleDept d : depts) {
            if (d.getId() != null) {
                byId.put(d.getId(), d);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (ModuleDept d : depts) {
            if (d.getId() == null || d.getStatus() == null || d.getStatus() != 1) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("value", d.getId());
            row.put("text", buildDeptLabel(d, byId));
            row.put("parentId", d.getParentId());
            row.put("depth", d.getDepth());
            out.add(row);
        }
        return out;
    }

    private static String buildDeptLabel(ModuleDept d, Map<Long, ModuleDept> byId) {
        String name = d.getDeptName() != null ? d.getDeptName() : d.getDeptCode();
        if (name == null) {
            name = "部门";
        }
        int level = depthLevel(d.getDepth());
        if (level > 0) {
            name = "  ".repeat(Math.min(level, 8)) + name;
        }
        return name;
    }

    private static int depthLevel(String depth) {
        if (depth == null || depth.isBlank()) {
            return 0;
        }
        return depth.split(",", -1).length - 1;
    }

    @Transactional(rollbackFor = Exception.class)
    public ModuleDept upsertDept(Long appId, Long operatorPlatId, UpsertDeptCmd body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        requireAppId(appId);
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.deptCode() == null || body.deptCode().isBlank()) {
            throw new BusinessException(400, "deptCode 不能为空");
        }
        if (body.deptName() == null || body.deptName().isBlank()) {
            throw new BusinessException(400, "deptName 不能为空");
        }
        int status = body.status() == null ? 1 : body.status();
        if (status != 1 && status != 2) {
            throw new BusinessException(400, "status 须为 1 或 2");
        }
        long parentId = body.parentId() == null ? 0L : body.parentId();
        String deptCode = body.deptCode().trim();
        String deptName = body.deptName().trim();

        ModuleDept dept;
        Long oldParentId = null;
        if (body.id() != null) {
            dept = moduleDeptService.getById(body.id());
            if (dept == null) {
                throw new BusinessException(404, "部门不存在");
            }
            if (!Objects.equals(dept.getSystemId(), systemId)
                    || !Objects.equals(dept.getTenantId(), tenantId)
                    || !Objects.equals(dept.getAppId(), appId)) {
                throw new BusinessException(403, "无权操作该部门");
            }
            oldParentId = dept.getParentId();
            validateParentNotCycle(dept.getId(), parentId, appId);
        } else {
            long existed = moduleDeptService.lambdaQuery()
                    .eq(ModuleDept::getAppId, appId)
                    .eq(ModuleDept::getDeptCode, deptCode)
                    .count();
            if (existed > 0) {
                throw new BusinessException(400, "deptCode 已存在");
            }
            dept = new ModuleDept();
            dept.setSystemId(systemId);
            dept.setTenantId(tenantId);
            dept.setAppId(appId);
            dept.setCreateUserId(operatorPlatId);
        }
        dept.setParentId(parentId);
        dept.setDeptCode(deptCode);
        dept.setDeptName(deptName);
        dept.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
        dept.setStatus(status);
        dept.setRemark(trim(body.remark()));
        dept.setUpdateUserId(operatorPlatId);
        if (body.id() != null) {
            moduleDeptService.updateById(dept);
        } else {
            moduleDeptService.save(dept);
        }
        boolean parentChanged = body.id() != null && !Objects.equals(oldParentId, parentId);
        applyDepthPath(dept, appId, parentId);
        if (parentChanged) {
            refreshSubtreeDepth(appId, dept.getId());
        }
        return dept;
    }

    /** 按 parentId 解析并写入 depth（祖先 ID + 本级 ID，逗号分隔） */
    private void applyDepthPath(ModuleDept dept, Long appId, long parentId) {
        if (dept == null || dept.getId() == null) {
            return;
        }
        String path = resolveDepthPath(appId, parentId, dept.getId());
        dept.setDepth(path);
        moduleDeptService.lambdaUpdate()
                .eq(ModuleDept::getId, dept.getId())
                .set(ModuleDept::getDepth, path)
                .update();
    }

    private String resolveDepthPath(Long appId, long parentId, long selfId) {
        if (parentId <= 0L) {
            return String.valueOf(selfId);
        }
        ModuleDept parent = moduleDeptService.getById(parentId);
        if (parent == null || !Objects.equals(parent.getAppId(), appId)) {
            throw new BusinessException(400, "父部门不存在或不属于当前应用");
        }
        if (parent.getDepth() == null || parent.getDepth().isBlank()) {
            applyDepthPath(parent, appId, parent.getParentId() == null ? 0L : parent.getParentId());
            parent = moduleDeptService.getById(parentId);
        }
        String parentPath = parent.getDepth();
        if (parentPath == null || parentPath.isBlank()) {
            throw new BusinessException(400, "父部门路径未初始化");
        }
        return parentPath + "," + selfId;
    }

    private void validateParentNotCycle(Long selfId, long newParentId, Long appId) {
        if (selfId == null || newParentId <= 0L) {
            return;
        }
        if (Objects.equals(selfId, newParentId)) {
            throw new BusinessException(400, "父部门不能是自己");
        }
        ModuleDept newParent = moduleDeptService.getById(newParentId);
        if (newParent == null || !Objects.equals(newParent.getAppId(), appId)) {
            return;
        }
        ModuleDept self = moduleDeptService.getById(selfId);
        if (self == null || self.getDepth() == null || self.getDepth().isBlank()) {
            return;
        }
        String selfPath = self.getDepth();
        String np = newParent.getDepth();
        if (np == null || np.isBlank()) {
            return;
        }
        if (np.equals(selfPath) || np.startsWith(selfPath + ",")) {
            throw new BusinessException(400, "不能将父部门设为自己的子部门");
        }
    }

    private void refreshSubtreeDepth(Long appId, Long parentDeptId) {
        if (parentDeptId == null) {
            return;
        }
        ModuleDept parent = moduleDeptService.getById(parentDeptId);
        if (parent == null || parent.getDepth() == null || parent.getDepth().isBlank()) {
            return;
        }
        List<ModuleDept> children = moduleDeptService.lambdaQuery()
                .eq(ModuleDept::getAppId, appId)
                .eq(ModuleDept::getParentId, parentDeptId)
                .list();
        for (ModuleDept child : children) {
            if (child.getId() == null) {
                continue;
            }
            String childPath = parent.getDepth() + "," + child.getId();
            child.setDepth(childPath);
            moduleDeptService.lambdaUpdate()
                    .eq(ModuleDept::getId, child.getId())
                    .set(ModuleDept::getDepth, childPath)
                    .update();
            refreshSubtreeDepth(appId, child.getId());
        }
    }

    /**
     * 查询某部门及其全部下级（含自身）的部门 ID 列表，依赖 depth 前缀匹配。
     */
    public List<Long> listSubtreeDeptIds(Long appId, Long deptId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        requireAppId(appId);
        if (deptId == null || deptId <= 0L) {
            return List.of();
        }
        ModuleDept root = moduleDeptService.getById(deptId);
        if (root == null || root.getDepth() == null || root.getDepth().isBlank()) {
            return List.of(deptId);
        }
        String prefix = root.getDepth();
        List<ModuleDept> all = listDepts(appId, operatorPlatId);
        List<Long> ids = new ArrayList<>();
        for (ModuleDept d : all) {
            if (d.getId() == null || d.getDepth() == null) {
                continue;
            }
            if (d.getDepth().equals(prefix) || d.getDepth().startsWith(prefix + ",")) {
                ids.add(d.getId());
            }
        }
        return ids;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDepts(Long operatorPlatId, List<Long> ids) {
        requireOperator(operatorPlatId);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            ModuleDept d = moduleDeptService.getById(id);
            if (d == null) {
                continue;
            }
            if (!Objects.equals(d.getSystemId(), systemId) || !Objects.equals(d.getTenantId(), tenantId)) {
                throw new BusinessException(403, "无权删除部门");
            }
            long children;
            if (d.getDepth() != null && !d.getDepth().isBlank()) {
                children = moduleDeptService.lambdaQuery()
                        .eq(ModuleDept::getAppId, d.getAppId())
                        .likeRight(ModuleDept::getDepth, d.getDepth() + ",")
                        .count();
            } else {
                children = moduleDeptService.lambdaQuery()
                        .eq(ModuleDept::getAppId, d.getAppId())
                        .eq(ModuleDept::getParentId, id)
                        .count();
            }
            if (children > 0) {
                throw new BusinessException(400, "请先删除子部门: " + (d.getDeptName() != null ? d.getDeptName() : id));
            }
            long members = moduleMemberService.lambdaQuery()
                    .eq(ModuleMember::getAppId, d.getAppId())
                    .eq(ModuleMember::getDeptId, id)
                    .count();
            if (members > 0) {
                throw new BusinessException(400, "部门下仍有成员，无法删除");
            }
            moduleDeptService.removeById(id);
        }
    }

    private static String trim(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private static void requireOperator(Long platId) {
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
    }

    private static long requireSystem() {
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        return systemId;
    }

    private static void requireAppId(Long appId) {
        if (appId == null || appId <= 0L) {
            throw new BusinessException(400, "appId 不能为空");
        }
    }
}
