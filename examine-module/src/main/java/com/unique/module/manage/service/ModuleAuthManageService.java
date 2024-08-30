package com.unique.module.manage.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.unique.core.entity.user.bo.SimpleUserRole;
import com.unique.module.entity.po.ModuleRoleUser;
import com.unique.module.service.*;
import com.unique.core.entity.base.vo.AuthVO;
import com.unique.core.entity.user.bo.SimpleDept;
import com.unique.core.entity.user.bo.SimpleUser;
import com.unique.core.enums.DataTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 获取用户相关权限
 * @author ceshi
 * @date 2024/07/01
 */
@Service
public class ModuleAuthManageService {
    @Autowired
    private IModuleUserService moduleUserService;
    @Autowired
    private IModuleDeptService moduleDeptService;
    @Autowired
    private IModuleRoleService moduleRoleService;
    @Autowired
    private IModuleRoleUserService moduleRoleUserService;
    @Autowired
    private IModuleMenuService moduleMenuService;

    public AuthVO moduleAuth(Long moduleId,Long userId) {
        AuthVO ret = new AuthVO();
        ret.setUserRoles(moduleRoleService.querySimpleRole(moduleId,userId));
        ret.setRoleMenus(moduleMenuService.querySimpleMenu(moduleId,userId));
        //数据
        List<SimpleUser> allUsers = moduleUserService.queryAllUsers(moduleId);
        List<SimpleDept> allDepts = moduleDeptService.queryAllDepts(moduleId);
        List<SimpleUser> curentUser = allUsers.stream().filter(f -> f.getId().equals(userId)).collect(Collectors.toList());
        if (CollectionUtil.isNotEmpty(curentUser)) {
            SimpleUser simpleUser = curentUser.get(0);
            ret.setSimpleUser(simpleUser);
            List<SimpleDept> currentDept = allDepts.stream().filter(f -> ObjectUtil.isNotEmpty(simpleUser.getDeptId()) && f.getId().equals(simpleUser.getDeptId())).collect(Collectors.toList());
            if (CollectionUtil.isNotEmpty(currentDept)) {
                ret.setSimpleDept(currentDept.get(0));
            }
        }
        //如果是超管
        if (StpUtil.hasRole("admin")) {
            ret.setDataUserIds(allUsers.stream().map(SimpleUser::getId).collect(Collectors.toSet()));
            ret.setDataSimpleUserIds(allUsers);
            return ret;
        }

        Set<Long> userIds = new HashSet<>();
        List<SimpleUserRole> roleUsers = moduleRoleUserService.queryDataType(moduleId, Arrays.asList(userId));

        roleUsers.forEach(r->{
            DataTypeEnum dataTypeEnum = DataTypeEnum.parse(r.getDataType());
            switch (dataTypeEnum){
                case SELF:
                    userIds.add(r.getUserId());
                    break;
                case SELF_AND_CHILD:
                    userIds.add(r.getUserId());
                    Set<Long> streamSet = allUsers.stream().filter(f -> ObjectUtil.isNotEmpty(f.getDeepth()) && f.getDeepth().contains(r.getUserId().toString()))
                            .map(SimpleUser::getId).collect(Collectors.toSet());
                    if (CollectionUtil.isNotEmpty(streamSet)) {
                        userIds.addAll(streamSet);
                    }
                    break;
                case DEPT:
                    if (ObjectUtil.isNotEmpty(r.getDeptId())) {
                        List<SimpleDept> subdepts = allDepts.stream().filter(d -> d.getDeepth().contains(r.getDeptId().toString())).collect(Collectors.toList());
                        if (CollectionUtil.isNotEmpty(subdepts)) {
                            List<Long> deptuserIds = allUsers.stream().filter(f -> ObjectUtil.isNotEmpty(f.getDeptId()) && subdepts.contains(f.getDeptId())).map(SimpleUser::getId).collect(Collectors.toList());
                            userIds.addAll(deptuserIds);
                        }
                    }
                    break;
                case DEPT_AND_CHILD:
                    if (ObjectUtil.isNotEmpty(r.getDeptId())) {
                        List<SimpleDept> subdepts = allDepts.stream().filter(d -> d.getDeepth().contains(r.getDeptId().toString())).collect(Collectors.toList());
                        if (CollectionUtil.isNotEmpty(subdepts)) {
                            Set<Long> itemIds = subdepts.stream().map(SimpleDept::getId).collect(Collectors.toSet());
                            itemIds.add(r.getDeptId());
                            if (CollectionUtil.isNotEmpty(itemIds)) {
                                Set<Long> streamSetAll = allUsers.stream().filter(f -> ObjectUtil.isNotEmpty(f.getDeptId()) && itemIds.contains(f.getDeptId())).map(SimpleUser::getId).collect(Collectors.toSet());
                                userIds.addAll(streamSetAll);
                            }
                        }
                    }
                    break;
            }
        });
        ret.setDataUserIds(userIds);
        if (CollectionUtil.isNotEmpty(userIds)) {
            ret.setDataSimpleUserIds(allUsers.stream().filter(f->userIds.contains(f.getId())).collect(Collectors.toList()));
        }
        return ret;
    }

    public Map<Long, List<Long>> querySuperUserGroupByUserId(Long moduleId) {
        List<SimpleUser> allUsers = moduleUserService.queryAllUsers(moduleId);
        Map<Long, List<Long>> ret = allUsers.stream().filter(f -> ObjectUtil.isNotEmpty(f.getParentId()))
                .collect(Collectors.groupingBy(SimpleUser::getParentId, Collectors.mapping(SimpleUser::getId, Collectors.toList())));
        return ObjectUtil.isNotEmpty(ret)?ret:new HashMap<>();
    }

    public Map<Long, List<Long>> queryDeptUserIdGroupByDeptId(Long moduleId) {
        List<SimpleDept> allDepts = moduleDeptService.queryAllDepts(moduleId);
        Map<Long, List<Long>> ret = allDepts.stream().filter(f -> ObjectUtil.isNotEmpty(f.getParentId()))
                .collect(Collectors.groupingBy(SimpleDept::getParentId, Collectors.mapping(SimpleDept::getId, Collectors.toList())));
        return ObjectUtil.isNotEmpty(ret)?ret:new HashMap<>();
    }

    public Map<Long, List<Long>> queryRoleUserIdGroupByRoleId(Long moduleId) {
        List<ModuleRoleUser> roleList = moduleRoleUserService.lambdaQuery().eq(ModuleRoleUser::getModuleId, moduleId).list();
        Map<Long, List<Long>> ret = roleList.stream().collect(Collectors.groupingBy(ModuleRoleUser::getRoleId, Collectors.mapping(ModuleRoleUser::getUserId, Collectors.toList())));
        return ObjectUtil.isNotEmpty(ret)?ret:new HashMap<>();
    }
}
