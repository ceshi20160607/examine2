package com.unique.admin.controller;


import com.unique.admin.common.utils.AuthUtil;
import com.unique.admin.service.manage.AdminAuthManageService;
import com.unique.core.common.Result;
import com.unique.core.entity.admin.vo.AuthVO;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 用户相关返回前端的权限
 * </p>
 *
 * @author UNIQUE
 * @since 2023-03-25
 */
@RestController
@RequestMapping("/adminUserManage")
public class AdminUserManageController {

    @Autowired
    private AdminAuthManageService adminAuthManageService;


}
