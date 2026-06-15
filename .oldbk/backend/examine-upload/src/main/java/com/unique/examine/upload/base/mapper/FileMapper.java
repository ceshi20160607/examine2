package com.unique.examine.upload.base.mapper;

import com.unique.examine.upload.base.entity.File;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件元数据、临时状态、对象存储定位和安全属性。 基础 Mapper。
 *
 * @author examine-generator
 * @since generated
 */
@Mapper
public interface FileMapper extends BaseMapper<File> {
}
