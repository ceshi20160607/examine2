package com.unique.unexamine.flow.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.flow.base.entity.UnFlowTaskCandidate;
import com.unique.unexamine.flow.base.mapper.UnFlowTaskCandidateMapper;
import com.unique.unexamine.flow.base.service.IUnFlowTaskCandidateService;
import org.springframework.stereotype.Service;

/** UnFlowTaskCandidate 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnFlowTaskCandidateServiceImpl extends ServiceImpl<UnFlowTaskCandidateMapper, UnFlowTaskCandidate> implements IUnFlowTaskCandidateService {
}
