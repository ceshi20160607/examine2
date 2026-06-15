package com.unique.examine.module.mapper;

import com.unique.examine.module.entity.po.ModuleRecordData;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 模型记录数据（*_data） Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-14
 */
public interface ModuleRecordDataMapper extends BaseMapper<ModuleRecordData> {

    List<ModuleRecordData> listByRecordIds(@Param("recordIds") Collection<Long> recordIds);

    List<ModuleRecordData> listByRecordIdsAndFieldCodes(
            @Param("recordIds") Collection<Long> recordIds,
            @Param("fieldCodes") Collection<String> fieldCodes);
}
