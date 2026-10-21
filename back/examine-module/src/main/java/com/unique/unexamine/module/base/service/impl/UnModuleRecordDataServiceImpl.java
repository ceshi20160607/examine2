package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.module.base.mapper.UnModuleRecordDataMapper;
import com.unique.unexamine.module.base.service.IUnModuleRecordDataService;
import org.springframework.stereotype.Service;

/** UnModuleRecordData 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleRecordDataServiceImpl extends ServiceImpl<UnModuleRecordDataMapper, UnModuleRecordData> implements IUnModuleRecordDataService {
}
