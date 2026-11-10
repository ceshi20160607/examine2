package com.unique.unexamine.sys.module.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * 模块字段 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since 2026-11-07
 */
@Mapper
public interface UnModuleFieldMapper extends BaseMapper<UnModuleField> {

    /** 读取模块全部历史字段键，包含逻辑删除字段，防止主表槽位被误复用。 */
    @Select("SELECT field_key FROM un_module_field WHERE module_id = #{moduleId}")
    List<String> selectAllFieldKeysForStorage(@Param("moduleId") Long moduleId);

}
