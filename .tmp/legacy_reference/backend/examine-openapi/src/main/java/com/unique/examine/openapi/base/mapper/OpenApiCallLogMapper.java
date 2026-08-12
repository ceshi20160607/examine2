package com.unique.examine.openapi.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.examine.openapi.base.entity.OpenApiCallLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OpenApiCallLogMapper extends BaseMapper<OpenApiCallLogEntity> {
}
