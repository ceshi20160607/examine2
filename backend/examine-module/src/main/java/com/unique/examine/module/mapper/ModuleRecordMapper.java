package com.unique.examine.module.mapper;

import com.unique.examine.module.entity.po.ModuleRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.examine.module.entity.dto.ModuleRecordDslQuery;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 模型记录主表 Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-14
 */
public interface ModuleRecordMapper extends BaseMapper<ModuleRecord> {

    Long countDsl(@Param("q") ModuleRecordDslQuery q);

    List<Map<String, Object>> listDsl(@Param("q") ModuleRecordDslQuery q,
                                      @Param("offset") long offset,
                                      @Param("limit") long limit);
}
