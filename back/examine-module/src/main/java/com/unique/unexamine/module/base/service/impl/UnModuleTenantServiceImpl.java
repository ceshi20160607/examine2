package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModuleTenant;
import com.unique.unexamine.module.base.mapper.UnModuleTenantMapper;
import com.unique.unexamine.module.base.service.IUnModuleTenantService;
import org.springframework.stereotype.Service;

/** UnModuleTenant 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleTenantServiceImpl extends ServiceImpl<UnModuleTenantMapper, UnModuleTenant> implements IUnModuleTenantService {
}
