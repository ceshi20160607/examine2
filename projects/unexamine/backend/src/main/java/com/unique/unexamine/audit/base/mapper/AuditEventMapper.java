package com.unique.unexamine.audit.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.audit.base.entity.AuditEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 不可变的业务操作审计事件 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@Mapper
public interface AuditEventMapper extends BaseMapper<AuditEvent> {

}
