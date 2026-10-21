package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModuleScence;
import com.unique.unexamine.module.base.mapper.UnModuleScenceMapper;
import com.unique.unexamine.module.base.service.IUnModuleScenceService;
import org.springframework.stereotype.Service;

/** UnModuleScence 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleScenceServiceImpl extends ServiceImpl<UnModuleScenceMapper, UnModuleScence> implements IUnModuleScenceService {
}
