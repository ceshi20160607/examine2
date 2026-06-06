package com.unique.examine.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.examine.base.entity.SerialSequence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SerialSequenceMapper extends BaseMapper<SerialSequence> {

    @org.apache.ibatis.annotations.Select("select * from serial_sequence where system_id = #{systemId} and tenant_id = #{tenantId} and module_id = #{moduleId} and sequence_key = #{sequenceKey} for update")
    SerialSequence selectForUpdate(@Param("systemId") Long systemId, @Param("tenantId") Long tenantId,
                                   @Param("moduleId") Long moduleId, @Param("sequenceKey") String sequenceKey);

}
