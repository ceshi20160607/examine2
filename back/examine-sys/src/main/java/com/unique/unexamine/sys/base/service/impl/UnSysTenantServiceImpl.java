package com.unique.unexamine.sys.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.sys.base.entity.UnSysTenant;
import com.unique.unexamine.sys.base.mapper.UnSysTenantMapper;
import com.unique.unexamine.sys.base.service.IUnSysTenantService;
import org.springframework.stereotype.Service;

/** UnSysTenant 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnSysTenantServiceImpl extends ServiceImpl<UnSysTenantMapper, UnSysTenant> implements IUnSysTenantService {
}
