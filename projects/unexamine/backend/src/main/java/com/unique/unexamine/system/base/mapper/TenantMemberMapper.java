package com.unique.unexamine.system.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.system.base.entity.TenantMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 系统成员在具体租户中的准入关系 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@Mapper
public interface TenantMemberMapper extends BaseMapper<TenantMember> {

}
