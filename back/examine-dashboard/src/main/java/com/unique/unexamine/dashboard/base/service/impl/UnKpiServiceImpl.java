package com.unique.unexamine.dashboard.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.dashboard.base.entity.UnKpi;
import com.unique.unexamine.dashboard.base.mapper.UnKpiMapper;
import com.unique.unexamine.dashboard.base.service.IUnKpiService;
import org.springframework.stereotype.Service;

/** UnKpi 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnKpiServiceImpl extends ServiceImpl<UnKpiMapper, UnKpi> implements IUnKpiService {
}
