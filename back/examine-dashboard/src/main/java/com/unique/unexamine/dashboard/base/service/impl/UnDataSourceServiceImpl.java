package com.unique.unexamine.dashboard.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.dashboard.base.entity.UnDataSource;
import com.unique.unexamine.dashboard.base.mapper.UnDataSourceMapper;
import com.unique.unexamine.dashboard.base.service.IUnDataSourceService;
import org.springframework.stereotype.Service;

/** UnDataSource 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnDataSourceServiceImpl extends ServiceImpl<UnDataSourceMapper, UnDataSource> implements IUnDataSourceService {
}
