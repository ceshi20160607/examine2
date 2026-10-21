package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModuleRelation;
import com.unique.unexamine.module.base.mapper.UnModuleRelationMapper;
import com.unique.unexamine.module.base.service.IUnModuleRelationService;
import org.springframework.stereotype.Service;

/** UnModuleRelation 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleRelationServiceImpl extends ServiceImpl<UnModuleRelationMapper, UnModuleRelation> implements IUnModuleRelationService {
}
