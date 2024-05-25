package com.unique.module.service.impl;

import com.unique.module.entity.po.Module;
import com.unique.module.mapper.ModuleMapper;
import com.unique.module.service.IModuleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 模块表 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-25
 */
@Service
public class ModuleServiceImpl extends ServiceImpl<ModuleMapper, Module> implements IModuleService {

}
