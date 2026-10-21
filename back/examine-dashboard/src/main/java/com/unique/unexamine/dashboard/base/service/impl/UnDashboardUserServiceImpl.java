package com.unique.unexamine.dashboard.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.dashboard.base.entity.UnDashboardUser;
import com.unique.unexamine.dashboard.base.mapper.UnDashboardUserMapper;
import com.unique.unexamine.dashboard.base.service.IUnDashboardUserService;
import org.springframework.stereotype.Service;

/** UnDashboardUser 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnDashboardUserServiceImpl extends ServiceImpl<UnDashboardUserMapper, UnDashboardUser> implements IUnDashboardUserService {
}
