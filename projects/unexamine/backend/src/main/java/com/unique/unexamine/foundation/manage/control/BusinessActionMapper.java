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
}
