package com.unique.unexamine.sys.module.manage.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/** ModuleRecordList 的请求与返回模型。 */
public final class ModuleRecordListDto {
    private ModuleRecordListDto() {}

    /** 列标识、字段名称和类型来自当前字段定义。 */
    public record Column(Long fieldId,String code,String name,int type,Integer widthPx,String fixedSide) {}

    /** 审批状态字段不是业务值字段，可由多个动作审批绑定共用。 */
    public record ApprovalStatusColumn(@Schema(description="页面状态组主键") Long groupId,
                                       @Schema(description="稳定状态字段编码") String code,
                                       @Schema(description="状态字段展示名称") String name,
                                       @Schema(description="列表展示顺序") int sortNo) {}

    /** 列表行含基础记录及当前有权读取的自定义字段值。 */
    public record Row(Long id,String name,String status,
                      @Schema(description="当前审批状态，0 为未提交") Integer checkStatus,Long ownerAccountId,
                      Long sourceTenantId,boolean shared,
                      Map<String,Object> values,Map<String,Object> valueDisplay,
                      @Schema(description="页面配置的各审批状态字段当前值") Map<String,String> approvalStatuses) {
        public Row(Long id,String name,String status,Long ownerAccountId,Long sourceTenantId,boolean shared,
                   Map<String,Object> values,Map<String,Object> valueDisplay,Map<String,String> approvalStatuses) {
            this(id,name,status,null,ownerAccountId,sourceTenantId,shared,values,valueDisplay,approvalStatuses);
        }
    }

    /** 列配置随本次查询返回，记录及页码使用统一的 MyBatis-Plus 分页结构。 */
    public record ListView(List<Column> columns,
                           @Schema(description="页面配置的审批状态字段") List<ApprovalStatusColumn> approvalStatusColumns,
                           Page<Row> page) {}
}
