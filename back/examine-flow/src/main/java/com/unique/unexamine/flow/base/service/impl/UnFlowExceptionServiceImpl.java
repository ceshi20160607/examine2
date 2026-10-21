package com.unique.unexamine.flow.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.flow.base.entity.UnFlowException;
import com.unique.unexamine.flow.base.mapper.UnFlowExceptionMapper;
import com.unique.unexamine.flow.base.service.IUnFlowExceptionService;
import org.springframework.stereotype.Service;

/** UnFlowException 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnFlowExceptionServiceImpl extends ServiceImpl<UnFlowExceptionMapper, UnFlowException> implements IUnFlowExceptionService {
}
