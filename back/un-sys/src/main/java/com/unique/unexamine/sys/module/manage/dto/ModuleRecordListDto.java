package com.unique.unexamine.sys.module.manage.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Map;

/** ModuleRecordList 的请求与返回模型。 */
public final class ModuleRecordListDto {
    private ModuleRecordListDto() {}

    /** 列标识、字段名称和类型来自当前字段定义。 */
    public record Column(Long fieldId,String code,String name,String type,Integer widthPx,String fixedSide) {}

    /** 列表行含基础记录及当前有权读取的自定义字段值。 */
    public record Row(Long id,String name,String status,Long ownerAccountId,
                      Long sourceTenantId,boolean shared,
                      Map<String,Object> values,Map<String,Object> valueDisplay) {}

    /** 列配置随本次查询返回，记录及页码使用统一的 MyBatis-Plus 分页结构。 */
    public record ListView(List<Column> columns,Page<Row> page) {}
}
