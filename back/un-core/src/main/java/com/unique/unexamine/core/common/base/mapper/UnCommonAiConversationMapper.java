package com.unique.unexamine.core.common.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.core.common.base.entity.UnCommonAiConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;

/** 智能对话追溯记录的基础持久化入口。 */
@Mapper
public interface UnCommonAiConversationMapper extends BaseMapper<UnCommonAiConversation> {
    /** 人工触发时按到期时间分批物理清理原文，避免逻辑删除仍保留敏感内容。 */
    @Delete("DELETE FROM un_common_ai_conversation WHERE expire_time <= NOW(3) LIMIT 1000")
    int purgeExpired();
}
