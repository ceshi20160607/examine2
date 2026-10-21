package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModuleRecord;
import com.unique.unexamine.module.base.mapper.UnModuleRecordMapper;
import com.unique.unexamine.module.base.service.IUnModuleRecordService;
import org.springframework.stereotype.Service;

/** UnModuleRecord 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleRecordServiceImpl extends ServiceImpl<UnModuleRecordMapper, UnModuleRecord> implements IUnModuleRecordService {
}
