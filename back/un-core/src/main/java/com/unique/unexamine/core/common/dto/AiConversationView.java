package com.unique.unexamine.core.common.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

/** 用户本人读取原始对话、预览与确认结果的受控展示模型。 */
public record AiConversationView(String conversationId, String scopeType, Long systemId, Long tenantId,
        Long configId, Long platformModelId, Integer systemPolicyVersion, Integer platformModelVersion,
        String promptVersion, String intentCode, String requestText, JsonNode proposal,
        String status, String targetType, Long targetId, Long confirmAccountId,
        LocalDateTime confirmTime, LocalDateTime createTime, LocalDateTime expireTime) {}
