package com.unique.unexamine.foundation.manage.control;

import com.unique.unexamine.foundation.base.entity.CoreIdempotencyRecord;
import com.unique.unexamine.foundation.base.entity.CoreEventOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BusinessActionMapper {
    void claim(@Param("contextKey") String contextKey, @Param("operation") String operation,
               @Param("key") String key, @Param("hash") String hash, @Param("claim") String claim);
    CoreIdempotencyRecord lock(@Param("contextKey") String contextKey, @Param("operation") String operation,
                               @Param("key") String key);
    CoreEventOutbox lockEvent(@Param("id") long id);
    CoreIdempotencyRecord lockResult(@Param("id") long id);
    com.unique.unexamine.flow.base.entity.FlowInstance lockApprovalFlow(@Param("id") long id);
    java.util.List<Long> completedApprovalActions();
    int activeActor(@Param("actor") com.unique.unexamine.authentication.manage.AuthenticatedContext actor);
    /** 仅通过内部 START 标识关联服务端原动作，不能相信用户填写的流程业务快照。 */
    CoreEventOutbox boundFlowEvent(@Param("instanceId") long instanceId);

    /** 本人当前成员上下文的内部审批动作候选；列表仍逐项检查当前业务授权。 */
    java.util.List<CoreEventOutbox> ownFlowEvents(
            @Param("actor") com.unique.unexamine.authentication.manage.AuthenticatedContext actor,
            @Param("cursor") Long cursor, @Param("pageSize") int pageSize);
}
