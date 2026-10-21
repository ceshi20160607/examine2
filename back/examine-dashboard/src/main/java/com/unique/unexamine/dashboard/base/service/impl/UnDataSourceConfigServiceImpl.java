package com.unique.unexamine.dashboard.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.dashboard.base.entity.UnDataSourceConfig;
import com.unique.unexamine.dashboard.base.mapper.UnDataSourceConfigMapper;
import com.unique.unexamine.dashboard.base.service.IUnDataSourceConfigService;
import org.springframework.stereotype.Service;

/** UnDataSourceConfig 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnDataSourceConfigServiceImpl extends ServiceImpl<UnDataSourceConfigMapper, UnDataSourceConfig> implements IUnDataSourceConfigService {
}
