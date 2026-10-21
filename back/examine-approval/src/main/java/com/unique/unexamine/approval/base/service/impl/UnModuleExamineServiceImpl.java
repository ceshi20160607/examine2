package com.unique.unexamine.approval.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.approval.base.entity.UnModuleExamine;
import com.unique.unexamine.approval.base.mapper.UnModuleExamineMapper;
import com.unique.unexamine.approval.base.service.IUnModuleExamineService;
import org.springframework.stereotype.Service;

/** UnModuleExamine 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleExamineServiceImpl extends ServiceImpl<UnModuleExamineMapper, UnModuleExamine> implements IUnModuleExamineService {
}
