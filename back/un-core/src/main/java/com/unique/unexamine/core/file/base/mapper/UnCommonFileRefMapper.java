package com.unique.unexamine.core.file.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.core.file.base.entity.UnCommonFileRef;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 文件与多个业务对象的引用关系 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@Mapper
public interface UnCommonFileRefMapper extends BaseMapper<UnCommonFileRef> {

}
