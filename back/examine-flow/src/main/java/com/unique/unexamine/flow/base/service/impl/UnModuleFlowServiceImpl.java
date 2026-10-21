package com.unique.unexamine.flow.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.flow.base.entity.UnModuleFlow;
import com.unique.unexamine.flow.base.mapper.UnModuleFlowMapper;
import com.unique.unexamine.flow.base.service.IUnModuleFlowService;
import org.springframework.stereotype.Service;

/** UnModuleFlow 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleFlowServiceImpl extends ServiceImpl<UnModuleFlowMapper, UnModuleFlow> implements IUnModuleFlowService {
}
