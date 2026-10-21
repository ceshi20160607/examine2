package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModuleRelationConfig;
import com.unique.unexamine.module.base.mapper.UnModuleRelationConfigMapper;
import com.unique.unexamine.module.base.service.IUnModuleRelationConfigService;
import org.springframework.stereotype.Service;

/** UnModuleRelationConfig 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleRelationConfigServiceImpl extends ServiceImpl<UnModuleRelationConfigMapper, UnModuleRelationConfig> implements IUnModuleRelationConfigService {
}
