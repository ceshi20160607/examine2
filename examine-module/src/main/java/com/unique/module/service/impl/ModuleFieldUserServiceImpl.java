package com.unique.module.service.impl;

import com.unique.module.entity.po.ModuleFieldUser;
import com.unique.module.mapper.ModuleFieldUserMapper;
import com.unique.module.service.IModuleFieldUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 自定义字段关联用户表 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-25
 */
@Service
public class ModuleFieldUserServiceImpl extends ServiceImpl<ModuleFieldUserMapper, ModuleFieldUser> implements IModuleFieldUserService {

}
