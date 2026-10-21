package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModuleScenceConfig;
import com.unique.unexamine.module.base.mapper.UnModuleScenceConfigMapper;
import com.unique.unexamine.module.base.service.IUnModuleScenceConfigService;
import org.springframework.stereotype.Service;

/** UnModuleScenceConfig 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleScenceConfigServiceImpl extends ServiceImpl<UnModuleScenceConfigMapper, UnModuleScenceConfig> implements IUnModuleScenceConfigService {
}
