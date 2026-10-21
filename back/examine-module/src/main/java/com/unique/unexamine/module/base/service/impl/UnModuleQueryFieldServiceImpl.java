package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModuleQueryField;
import com.unique.unexamine.module.base.mapper.UnModuleQueryFieldMapper;
import com.unique.unexamine.module.base.service.IUnModuleQueryFieldService;
import org.springframework.stereotype.Service;

/** UnModuleQueryField 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleQueryFieldServiceImpl extends ServiceImpl<UnModuleQueryFieldMapper, UnModuleQueryField> implements IUnModuleQueryFieldService {
}
