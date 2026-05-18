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
            out.add(row);
        }
        return out;
    }

    private static String buildDeptLabel(ModuleDept d, Map<Long, ModuleDept> byId) {
        String name = d.getDeptName() != null ? d.getDeptName() : d.getDeptCode();
        if (name == null) {
            name = "部门";
        }
        Long pid = d.getParentId();
        if (pid == null || pid <= 0L || !byId.containsKey(pid)) {
            return name;
        }
        ModuleDept parent = byId.get(pid);
        String pn = parent != null && parent.getDeptName() != null ? parent.getDeptName() : parent != null ? parent.getDeptCode() : null;
        return pn != null && !pn.isBlank() ? pn + " / " + name : name;
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
        return dept;
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
            long children = moduleDeptService.lambdaQuery()
                    .eq(ModuleDept::getAppId, d.getAppId())
                    .eq(ModuleDept::getParentId, id)
                    .count();
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
