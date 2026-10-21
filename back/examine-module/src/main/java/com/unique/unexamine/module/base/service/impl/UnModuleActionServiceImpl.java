package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModuleAction;
import com.unique.unexamine.module.base.mapper.UnModuleActionMapper;
import com.unique.unexamine.module.base.service.IUnModuleActionService;
import org.springframework.stereotype.Service;

/** UnModuleAction 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleActionServiceImpl extends ServiceImpl<UnModuleActionMapper, UnModuleAction> implements IUnModuleActionService {
}
