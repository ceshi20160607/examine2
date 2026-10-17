package com.unique.unexamine.moduleconfig.manage.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 模块发布前，针对运行记录与字段值的跨表兼容性检查。 */
@Mapper
public interface ModulePublicationCheckRepository {

    /** 统计拟停用字段在未删除的模块记录中仍有值的数量。 */
    @Select("SELECT COUNT(*) FROM un_biz_record_value v JOIN un_biz_record r ON r.id = v.record_id "
            + "WHERE r.module_id = #{moduleId} AND r.deleted = 0 AND v.field_id = #{fieldId}")
    int countFieldValues(@Param("moduleId") Long moduleId, @Param("fieldId") Long fieldId);

    /** 统计子表容器下仍存在的有效明细行。 */
    @Select("SELECT COUNT(*) FROM un_biz_record_child_row cr JOIN un_biz_record r ON r.id = cr.record_id "
            + "WHERE cr.module_id = #{moduleId} AND cr.child_field_id = #{fieldId} "
            + "AND cr.deleted = 0 AND r.deleted = 0")
    int countChildRows(@Param("moduleId") Long moduleId, @Param("fieldId") Long fieldId);

    /** 子表改为必填时，统计尚无该子表明细行的有效主记录。 */
    @Select("SELECT COUNT(*) FROM un_biz_record r LEFT JOIN un_biz_record_child_row cr "
            + "ON cr.record_id = r.id AND cr.child_field_id = #{fieldId} AND cr.deleted = 0 "
            + "WHERE r.module_id = #{moduleId} AND r.deleted = 0 AND cr.id IS NULL")
    int countRecordsMissingChildRows(@Param("moduleId") Long moduleId, @Param("fieldId") Long fieldId);

    /** 统计子表列在有效明细行中已有的值。 */
    @Select("SELECT COUNT(*) FROM un_biz_record_child_value v JOIN un_biz_record_child_row cr ON cr.id = v.child_row_id "
            + "JOIN un_biz_record r ON r.id = cr.record_id WHERE cr.module_id = #{moduleId} "
            + "AND v.field_id = #{fieldId} AND cr.deleted = 0 AND r.deleted = 0")
    int countChildFieldValues(@Param("moduleId") Long moduleId, @Param("fieldId") Long fieldId);

    /** 统计新增必填约束影响的既有记录；多选空数组也属于缺失值。 */
    @Select("SELECT COUNT(*) FROM un_biz_record r LEFT JOIN un_biz_record_value v "
            + "ON v.record_id = r.id AND v.field_id = #{fieldId} "
            + "WHERE r.module_id = #{moduleId} AND r.deleted = 0 AND "
            + "(v.id IS NULL OR (v.value_text IS NULL AND v.value_number IS NULL AND v.value_date IS NULL "
            + "AND v.value_time IS NULL AND v.value_boolean IS NULL AND v.value_reference_id IS NULL "
            + "AND v.value_file_id IS NULL AND v.value_json IS NULL) "
            + "OR (#{fieldType} = 'MULTI_SELECT' AND JSON_TYPE(v.value_json) = 'ARRAY' "
            + "AND JSON_LENGTH(v.value_json) = 0))")
    int countRecordsMissingField(@Param("fieldId") Long fieldId, @Param("moduleId") Long moduleId,
                                 @Param("fieldType") String fieldType);

    /** 子表新增必填列时，按有效明细行而不是主记录检查缺失值。 */
    @Select("SELECT COUNT(*) FROM un_biz_record_child_row cr JOIN un_biz_record r ON r.id = cr.record_id "
            + "LEFT JOIN un_biz_record_child_value v ON v.child_row_id = cr.id AND v.field_id = #{fieldId} "
            + "WHERE cr.module_id = #{moduleId} AND cr.child_field_id = #{parentFieldId} "
            + "AND cr.deleted = 0 AND r.deleted = 0 AND "
            + "(v.id IS NULL OR (v.value_text IS NULL AND v.value_number IS NULL AND v.value_date IS NULL "
            + "AND v.value_time IS NULL AND v.value_boolean IS NULL AND v.value_reference_id IS NULL "
            + "AND v.value_file_id IS NULL AND v.value_json IS NULL) "
            + "OR (#{fieldType} = 'MULTI_SELECT' AND JSON_TYPE(v.value_json) = 'ARRAY' "
            + "AND JSON_LENGTH(v.value_json) = 0))")
    int countChildRowsMissingField(@Param("fieldId") Long fieldId, @Param("parentFieldId") Long parentFieldId,
                                   @Param("moduleId") Long moduleId, @Param("fieldType") String fieldType);
}
