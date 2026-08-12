package com.unique.examine.openapi.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.examine.openapi.base.entity.OpenApiNonceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OpenApiNonceMapper extends BaseMapper<OpenApiNonceEntity> {
}
