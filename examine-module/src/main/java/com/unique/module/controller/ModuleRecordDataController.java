package com.unique.module.controller;


import cn.hutool.core.util.ObjectUtil;
import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;
import com.unique.core.common.Result;
import com.unique.module.entity.po.ModuleField;
import com.unique.module.service.IModuleRecordDataService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


import com.unique.module.entity.po.ModuleRecordData;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 主数据自定义字段存值表 前端控制器
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@RestController
@RequestMapping("/moduleRecordData")
@Api(tags = "主数据自定义字段存值表")
public class ModuleRecordDataController {

}

