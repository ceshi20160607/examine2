package com.unique.unexamine.flow.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.flow.base.entity.UnFlowBinding;
import com.unique.unexamine.flow.base.mapper.UnFlowBindingMapper;
import com.unique.unexamine.flow.base.service.IUnFlowBindingService;
import org.springframework.stereotype.Service;

/** UnFlowBinding 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnFlowBindingServiceImpl extends ServiceImpl<UnFlowBindingMapper, UnFlowBinding> implements IUnFlowBindingService {
}
