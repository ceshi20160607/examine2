package com.unique.admin.common.utils;

import cn.dev33.satoken.secure.SaSecureUtil;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import com.unique.admin.entity.po.AdminUser;
import com.unique.admin.service.IAdminDeptService;
import com.unique.admin.service.IAdminMenuService;
import com.unique.admin.service.IAdminRoleService;
import com.unique.admin.service.IAdminUserService;
import com.unique.admin.service.manage.AdminAuthManageService;
import com.unique.core.context.Const;
import com.unique.core.entity.admin.vo.AuthVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * 权限
 * @author ceshi
 * @date 2024/06/25
 */
@Slf4j
@Component
public class AuthUtil {
    static AuthUtil ME;

    @Autowired
    private AdminAuthManageService adminAuthManageService;


    /**
     * 管理员权限
     * @param userId
     * @return {@link Boolean }
     */
    public static Boolean adminFlag(Long userId) {
        Boolean boolFlag = Boolean.FALSE;
        userId = ObjectUtil.isEmpty(userId)?userId:StpUtil.getLoginIdAsLong();
//        StpUtil.checkPermission();
        return boolFlag;
    }

    /**
     * 权限
     * @param userId
     * @return {@link AuthVO }
     */
    public static AuthVO queryAuth(Long userId) {
        AuthVO authVO = new AuthVO();
        userId = ObjectUtil.isEmpty(userId)?userId:StpUtil.getLoginIdAsLong();
//        StpUtil.checkPermission();
        authVO = ME.adminAuthManageService.queryAuth(userId);
        return authVO;
    }
}
