package com.unique.admin.service.manage;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.unique.admin.service.IAdminDeptService;
import com.unique.admin.service.IAdminMenuService;
import com.unique.admin.service.IAdminRoleService;
import com.unique.admin.service.IAdminUserService;
import com.unique.core.entity.admin.vo.AuthVO;
import com.unique.core.entity.user.bo.SimpleDept;
import com.unique.core.entity.user.bo.SimpleUser;
import com.unique.core.enums.DataTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminAuthManageService {
    @Autowired
    private IAdminUserService adminUserService;
    @Autowired
    private IAdminDeptService adminDeptService;
    @Autowired
    private IAdminRoleService adminRoleService;
    @Autowired
    private IAdminMenuService adminMenuService;

    public AuthVO queryAuth(Long userId) {
        AuthVO ret = new AuthVO();
        ret.setUserRoles(adminRoleService.querySimpleRole(userId));
        ret.setRoleMenus(adminMenuService.querySimpleMenu(userId));
        //数据
        List<SimpleUser> allUsers = adminUserService.queryAllUsers();
        List<SimpleDept> allDepts = adminDeptService.queryAllDepts();
        //用户
        Map<Long, SimpleUser> allUsersMap = allUsers.stream().collect(Collectors.toMap(SimpleUser::getId, r -> r));
        //用户
        Map<Long, SimpleDept> allDeptsMap = allDepts.stream().collect(Collectors.toMap(SimpleDept::getId, r -> r));
        //直属用户
        Map<Long, List<SimpleUser>> allUsersParentMap = allUsers.stream().collect(Collectors.groupingBy(SimpleUser::getParentId));
        //部门用户
        Map<Long, List<SimpleUser>> allUsersDeptMap = allUsers.stream().collect(Collectors.groupingBy(SimpleUser::getDeptId));


        ret.setSimpleUser(allUsersMap.get(userId));
        ret.setSimpleDept(allDeptsMap.get(userId));


        List<SimpleUser> dataSimpleUserIds = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();
        List<Long> deptIds = new ArrayList<>();
        List<SimpleDept> dataSimpleDeptIds =  adminDeptService.queryDataDepts(userId);
        if (CollectionUtil.isNotEmpty(dataSimpleDeptIds)) {
            dataSimpleDeptIds.forEach(r->{
                DataTypeEnum dataTypeEnum = DataTypeEnum.parse(r.getDataType());
                SimpleUser itemuser = allUsersMap.get(StpUtil.getLoginIdAsLong());
                deptIds.add(r.getId());
                switch (dataTypeEnum){
                    case SELF:
                        userIds.add(StpUtil.getLoginIdAsLong());
                        dataSimpleUserIds.add(itemuser);
                        break;
                    case SELF_AND_CHILD:
                        userIds.add(StpUtil.getLoginIdAsLong());
                        dataSimpleUserIds.add(itemuser);
                        List<SimpleUser> subusers = allUsersParentMap.get(itemuser.getId());
                        if (CollectionUtil.isNotEmpty(subusers)) {
                            List<Long> subuserIds = subusers.stream().map(SimpleUser::getId).collect(Collectors.toList());
                            userIds.addAll(subuserIds);
                            dataSimpleUserIds.addAll(subusers);
                        }
                        break;
                    case DEPT:
                        List<SimpleUser> simpleUsers = allUsersDeptMap.get(r.getId());
                        if (CollectionUtil.isNotEmpty(simpleUsers)) {
                            List<Long> deptuserIds = simpleUsers.stream().map(SimpleUser::getId).collect(Collectors.toList());
                            userIds.addAll(deptuserIds);
                        }
                        break;
                    case DEPT_AND_CHILD:
                        List<SimpleDept> subdepts = allDepts.stream().filter(d -> d.getDeepth().contains(d.getId().toString())).collect(Collectors.toList());
                        if (CollectionUtil.isNotEmpty(subdepts)) {
                            List<Long> itemIds = subdepts.stream().map(SimpleDept::getId).collect(Collectors.toList());
                            deptIds.addAll(itemIds);
                        }
                        break;
                }
            });
        }
        ret.setDataDeptIds(deptIds);
        ret.setDataUserIds(userIds);

        ret.setDataSimpleUserIds(dataSimpleUserIds);
        ret.setDataSimpleDeptIds(dataSimpleDeptIds);
        return ret;
    }
}
