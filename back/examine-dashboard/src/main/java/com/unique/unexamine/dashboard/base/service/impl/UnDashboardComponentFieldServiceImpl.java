package com.unique.unexamine.dashboard.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.dashboard.base.entity.UnDashboardComponentField;
import com.unique.unexamine.dashboard.base.mapper.UnDashboardComponentFieldMapper;
import com.unique.unexamine.dashboard.base.service.IUnDashboardComponentFieldService;
import org.springframework.stereotype.Service;

/** UnDashboardComponentField 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnDashboardComponentFieldServiceImpl extends ServiceImpl<UnDashboardComponentFieldMapper, UnDashboardComponentField> implements IUnDashboardComponentFieldService {
}
