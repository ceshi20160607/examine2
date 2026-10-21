package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModuleField;
import com.unique.unexamine.module.base.mapper.UnModuleFieldMapper;
import com.unique.unexamine.module.base.service.IUnModuleFieldService;
import org.springframework.stereotype.Service;

/** UnModuleField 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleFieldServiceImpl extends ServiceImpl<UnModuleFieldMapper, UnModuleField> implements IUnModuleFieldService {
}
