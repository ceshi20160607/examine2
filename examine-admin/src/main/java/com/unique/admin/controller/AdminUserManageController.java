package com.unique.admin.controller;


import com.unique.admin.service.manage.AdminAuthManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
