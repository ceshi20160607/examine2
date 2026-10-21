package com.unique.unexamine.dashboard.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.dashboard.base.entity.UnKpiUser;
import com.unique.unexamine.dashboard.base.mapper.UnKpiUserMapper;
import com.unique.unexamine.dashboard.base.service.IUnKpiUserService;
import org.springframework.stereotype.Service;

/** UnKpiUser 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnKpiUserServiceImpl extends ServiceImpl<UnKpiUserMapper, UnKpiUser> implements IUnKpiUserService {
}
