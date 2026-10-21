package ${package.Service};

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import ${package.Entity}.${entity};
import java.util.List;
import java.util.Map;

/** ${table.comment!entity}的基础服务；租户权限和跨表规则在同模块 manage 中处理。 */
public interface ${table.serviceName} extends IService<${entity}> {
    /** 分页和排序仅接受本表白名单字段。 */
    Page<${entity}> queryPageList(PageQuery query);

    /** 主键为空时新增，否则更新已存在记录。 */
    ${entity} addOrUpdate(${entity} model);

    /** 按主键读取本表记录；不存在时明确失败。 */
    ${entity} queryById(Long id);

    /** 返回基础字段及其默认布局。 */
    List<FieldView> information(Long id);

    /** 用模块字段宽度、顺序和扩展值组成真实详情布局。 */
    List<FieldView> information(Long id, List<FieldLayout> layout, Map<String, Object> extensionValues);

    /** 按主键批量删除本表记录。 */
    boolean deleteByIds(List<Long> ids);

    /** 通用列表的等值筛选、分页和安全排序参数。 */
    record PageQuery(long page, long size, Map<String, Object> filters,
                     String sortField, boolean descending) {
    }

    /** 模块字段配置中的显示宽度；50 表示半行。 */
    record FieldLayout(String fieldName, String label, int widthPercent, int sortOrder) {
    }

    /** 详情字段值及按宽度计算出的行号和行内偏移。 */
    record FieldView(String fieldName, String label, Object value,
                     int row, int leftPercent, int widthPercent) {
    }
}
