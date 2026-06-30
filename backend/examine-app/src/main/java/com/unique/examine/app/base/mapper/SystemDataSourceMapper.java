package com.unique.examine.app.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.examine.app.base.entity.SystemDataSource;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SystemDataSourceMapper extends BaseMapper<SystemDataSource> {

    String TABLE_NAME = SystemDataSource.TABLE_NAME;
}
