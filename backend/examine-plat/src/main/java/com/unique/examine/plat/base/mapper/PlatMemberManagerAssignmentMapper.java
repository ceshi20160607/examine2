package com.unique.examine.plat.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.examine.plat.base.entity.MemberManagerAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PlatMemberManagerAssignmentMapper extends BaseMapper<MemberManagerAssignment> {
    @Select("""
            SELECT id
              FROM un_plat_tenant
             WHERE system_id = #{systemId}
               AND id = #{tenantId}
             FOR UPDATE
            """)
    Long lockTenant(
            @Param("systemId") long systemId,
            @Param("tenantId") long tenantId
    );

    @Update("""
            UPDATE un_plat_member_manager_assignment
               SET status = 'CLEARED',
                   cleared_by = #{clearedBy},
                   cleared_at = #{clearedAt},
                   version = version + 1
             WHERE system_id = #{systemId}
               AND tenant_id = #{tenantId}
               AND assignment_id = #{assignmentId}
               AND status = 'ACTIVE'
               AND version = #{expectedVersion}
            """)
    int clearActive(
            @Param("systemId") long systemId,
            @Param("tenantId") long tenantId,
            @Param("assignmentId") long assignmentId,
            @Param("expectedVersion") long expectedVersion,
            @Param("clearedBy") long clearedBy,
            @Param("clearedAt") LocalDateTime clearedAt
    );
}
