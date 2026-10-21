package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModuleFieldSort;
import com.unique.unexamine.module.base.mapper.UnModuleFieldSortMapper;
import com.unique.unexamine.module.base.service.IUnModuleFieldSortService;
import org.springframework.stereotype.Service;

/** UnModuleFieldSort 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleFieldSortServiceImpl extends ServiceImpl<UnModuleFieldSortMapper, UnModuleFieldSort> implements IUnModuleFieldSortService {
}
