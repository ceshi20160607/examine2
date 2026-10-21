package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModuleDict;
import com.unique.unexamine.module.base.mapper.UnModuleDictMapper;
import com.unique.unexamine.module.base.service.IUnModuleDictService;
import org.springframework.stereotype.Service;

/** UnModuleDict 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleDictServiceImpl extends ServiceImpl<UnModuleDictMapper, UnModuleDict> implements IUnModuleDictService {
}
