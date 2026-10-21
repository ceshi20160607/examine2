package com.unique.unexamine.flow.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.flow.base.entity.UnFlowVersion;
import com.unique.unexamine.flow.base.mapper.UnFlowVersionMapper;
import com.unique.unexamine.flow.base.service.IUnFlowVersionService;
import org.springframework.stereotype.Service;

/** UnFlowVersion 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnFlowVersionServiceImpl extends ServiceImpl<UnFlowVersionMapper, UnFlowVersion> implements IUnFlowVersionService {
}
