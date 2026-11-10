package com.unique.unexamine.core.common.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.core.common.base.entity.UnCommonAiConversation;
import com.unique.unexamine.core.common.base.mapper.UnCommonAiConversationMapper;
import com.unique.unexamine.core.common.base.service.IUnCommonAiConversationService;
import org.springframework.stereotype.Service;

/** 智能对话记录的基础持久化实现。 */
@Service
public class UnCommonAiConversationServiceImpl
        extends ServiceImpl<UnCommonAiConversationMapper, UnCommonAiConversation>
        implements IUnCommonAiConversationService {
    /** 清理明确过期的对话，保留期内不允许物理删除。 */
    @Override
    public int purgeExpired() { return baseMapper.purgeExpired(); }
}
