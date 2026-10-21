package com.unique.unexamine.dashboard.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.dashboard.base.entity.UnDashboard;
import com.unique.unexamine.dashboard.base.mapper.UnDashboardMapper;
import com.unique.unexamine.dashboard.base.service.IUnDashboardService;
import org.springframework.stereotype.Service;

/** UnDashboard 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnDashboardServiceImpl extends ServiceImpl<UnDashboardMapper, UnDashboard> implements IUnDashboardService {
}
