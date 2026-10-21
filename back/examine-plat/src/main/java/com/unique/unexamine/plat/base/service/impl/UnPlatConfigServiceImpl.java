package com.unique.unexamine.plat.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.plat.base.entity.UnPlatConfig;
import com.unique.unexamine.plat.base.mapper.UnPlatConfigMapper;
import com.unique.unexamine.plat.base.service.IUnPlatConfigService;
import org.springframework.stereotype.Service;

/** UnPlatConfig 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnPlatConfigServiceImpl extends ServiceImpl<UnPlatConfigMapper, UnPlatConfig> implements IUnPlatConfigService {
}
