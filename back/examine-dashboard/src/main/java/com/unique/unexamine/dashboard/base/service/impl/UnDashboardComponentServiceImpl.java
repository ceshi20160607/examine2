package com.unique.unexamine.dashboard.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.dashboard.base.entity.UnDashboardComponent;
import com.unique.unexamine.dashboard.base.mapper.UnDashboardComponentMapper;
import com.unique.unexamine.dashboard.base.service.IUnDashboardComponentService;
import org.springframework.stereotype.Service;

/** UnDashboardComponent 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnDashboardComponentServiceImpl extends ServiceImpl<UnDashboardComponentMapper, UnDashboardComponent> implements IUnDashboardComponentService {
}
