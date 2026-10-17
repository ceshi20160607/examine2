package com.unique.unexamine.moduleconfig.manage.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 字典项停用前，对模块字段和有效业务记录的跨表使用统计。 */
@Mapper
public interface DictionaryUsageRepository {

    /** 文本值与多选 JSON 值都可能引用字典项，历史已删除记录不阻断停用。 */
    @Select("SELECT COUNT(*) FROM un_biz_record_value v "
            + "JOIN un_cfg_module_field f ON f.id = v.field_id "
            + "JOIN un_biz_record r ON r.id = v.record_id AND r.deleted = 0 "
            + "WHERE f.dictionary_id = #{dictionaryId} "
            + "AND (v.value_text = #{itemCode} "
            + "OR JSON_CONTAINS(v.value_json, JSON_QUOTE(#{itemCode}), '$'))")
    long countActiveRecordValues(@Param("dictionaryId") Long dictionaryId,
                                 @Param("itemCode") String itemCode);
}
