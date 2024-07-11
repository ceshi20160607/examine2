package com.unique.admin.controller;


import com.unique.admin.common.utils.AuthUtil;
import com.unique.core.common.Result;
import com.unique.core.entity.admin.vo.AuthVO;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 菜单权限配置表 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2023-03-25
 */
@RestController
@RequestMapping("/adminMenu")
public class AdminMenuController {

    @PostMapping("/loginAuth")
    @ApiOperation("根据ID删除数据")
    public Result deleteById() {
        AuthVO authVO = AuthUtil.queryAuth(null);
        return Result.ok(authVO);
    }
}
