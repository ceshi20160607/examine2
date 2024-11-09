package com.unique.module.manage.controller;


import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.unique.core.common.Result;
import com.unique.core.context.BaseConst;
import com.unique.core.entity.admin.bo.UserBO;
import com.unique.core.entity.base.vo.AuthVO;
import com.unique.core.entity.user.bo.SimpleRole;
import com.unique.core.enums.SystemCodeEnum;
import com.unique.core.enums.UserStatusEnum;
import com.unique.core.utils.EncryptUtil;
import com.unique.module.entity.po.ModuleUser;
import com.unique.module.manage.service.ModuleAuthManageService;
import com.unique.module.service.IModuleRoleUserService;
import com.unique.module.service.IModuleUserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * <p>
 * 管理的权限 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@RestController
@RequestMapping("/moduleAdmin")
@Api(tags = "管理的权限")
@Slf4j
public class ManageAuthController {

    @Autowired
    private IModuleUserService moduleUserService;
    @Autowired
    private IModuleRoleUserService moduleRoleUserService;
    @Autowired
    private ModuleAuthManageService moduleAuthManageService;
    @PostMapping("/doLogin")
    public Result doLogin(@RequestBody UserBO userBO) {
        // 此处仅作模拟示例，真实项目需要从数据库中查询数据进行比对
        List<ModuleUser> list = moduleUserService.lambdaQuery()
                .eq(ModuleUser::getUsername, userBO.getUsername())
                .eq(ModuleUser::getStatus, UserStatusEnum.NORMAL.getType()).list();
        if (CollectionUtil.isNotEmpty(list)) {
            ModuleUser adminUser = list.get(0);
            if (EncryptUtil.checkUserPwd(adminUser.getUsername(),userBO.getPassword(),adminUser.getSalt(),adminUser.getPassword())) {
                StpUtil.login(adminUser.getId(), userBO.getDeviceType().getRemarks());
                SaSession session = StpUtil.getSession();
                session.set(BaseConst.DEFAULT_SESSION_USER_KEY + adminUser.getId(), adminUser);
                log.info("****:"+StpUtil.getTokenInfo().toString());
                return Result.ok(StpUtil.getTokenInfo());
            }
        }
        return Result.error(SystemCodeEnum.SYSTEM_NOT_LOGIN);
    }

    @PostMapping("/doLoginTest")
    public Result doLoginTest(@RequestBody UserBO userBO) {
        // 此处仅作模拟示例，真实项目需要从数据库中查询数据进行比对
        List<ModuleUser> list = moduleUserService.lambdaQuery().list();
        if (CollectionUtil.isNotEmpty(list)) {
            ModuleUser adminUser = list.get(0);
            if (EncryptUtil.checkUserPwd(adminUser.getUsername(),userBO.getPassword(),adminUser.getSalt(),adminUser.getPassword())) {
                StpUtil.login(adminUser.getId(), userBO.getDeviceType().getRemarks());
                List<SimpleRole> roleUserList  = moduleRoleUserService.queryAllRoleUser(adminUser.getModuleId(), null, Arrays.asList(adminUser.getId()));
                adminUser.setAdminFlag(CollectionUtil.isNotEmpty(roleUserList) && roleUserList.stream().anyMatch(f -> f.getAdminFlag().equals(1))?1:0);
                adminUser.setUserRoleList(roleUserList);
                SaSession session = StpUtil.getSession();
                session.set(BaseConst.DEFAULT_SESSION_USER_KEY + adminUser.getId(), adminUser);
                log.info("****:"+StpUtil.getTokenInfo().toString());
                return Result.ok(StpUtil.getTokenInfo());
            }
        }
        return Result.error(SystemCodeEnum.SYSTEM_NOT_LOGIN);
    }

    @GetMapping("/isLogin")
    public Result isLogin() {
        return Result.ok("是否登录：" + StpUtil.isLogin());
    }


    @GetMapping("/logout")
    public Result logout() {
        StpUtil.logout();
        return Result.ok();
    }

    @PostMapping("/auth")
    @ApiOperation("管理的权限")
    public Result moduleAuth() {
        SaSession session = StpUtil.getSession();
        ModuleUser loginUser = (ModuleUser)session.get(BaseConst.DEFAULT_SESSION_USER_KEY + StpUtil.getLoginIdAsLong());
        AuthVO authVO = moduleAuthManageService.moduleAuth(loginUser.getModuleId(),StpUtil.getLoginIdAsLong());
        return Result.ok(authVO);
    }
}

