package com.unique.examine.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.examine.base.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
