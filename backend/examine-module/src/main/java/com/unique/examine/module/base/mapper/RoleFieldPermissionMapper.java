package com.unique.examine.module.base.mapper;

import com.unique.examine.module.base.entity.RoleFieldPermission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字段可见、可写、导出明文和 OpenAPI 读写授权。 基础 Mapper。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Mapper
public interface RoleFieldPermissionMapper extends BaseMapper<RoleFieldPermission> {
}
