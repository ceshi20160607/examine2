package com.unique.unexamine.core.common.base.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.unique.unexamine.core.common.base.entity.UnCommonAiConversation;

/** 智能对话基础服务；访问控制由平台与系统运行服务执行。 */
public interface IUnCommonAiConversationService extends IService<UnCommonAiConversation> {
    /** 每次最多清理一千条已到期原文记录。 */
    int purgeExpired();
}
