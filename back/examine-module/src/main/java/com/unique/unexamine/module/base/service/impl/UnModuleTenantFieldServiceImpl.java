package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModuleTenantField;
import com.unique.unexamine.module.base.mapper.UnModuleTenantFieldMapper;
import com.unique.unexamine.module.base.service.IUnModuleTenantFieldService;
import org.springframework.stereotype.Service;

/** UnModuleTenantField 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleTenantFieldServiceImpl extends ServiceImpl<UnModuleTenantFieldMapper, UnModuleTenantField> implements IUnModuleTenantFieldService {
}
