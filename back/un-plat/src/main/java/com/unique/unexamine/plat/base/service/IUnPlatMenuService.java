package com.unique.unexamine.plat.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.unique.unexamine.plat.base.entity.UnPlatMenu;
import java.util.List;
import java.util.Map;

/** 平台菜单的基础服务；租户权限和跨表规则在同模块 manage 中处理。 */
public interface IUnPlatMenuService extends IService<UnPlatMenu> {
    /** 分页和排序仅接受本表白名单字段。 */
    Page<UnPlatMenu> queryPageList(PageQuery query);

    /** 主键为空时新增，否则更新已存在记录。 */
    UnPlatMenu addOrUpdate(UnPlatMenu model);

    /** 按主键读取本表记录；不存在时明确失败。 */
    UnPlatMenu queryById(Long id);

    /** 按已保存的表单字段配置填充详情值，配置及其顺序保持原样。 */
    List<FieldView> information(Long id, List<FieldConfig> configurations, Map<String, Object> extensionValues);

    /** 按主键批量删除本表记录。 */
    boolean deleteByIds(List<Long> ids);

    /** 通用列表的等值筛选、分页和安全排序参数。 */
    record PageQuery(long page, long size, Map<String, Object> filters,
                     String sortField, boolean descending) {
    }

    /** 已保存的字段配置；configuration 包含字段类型、拖拽位置、占位宽度等原始配置。 */
    record FieldConfig(String fieldName, Map<String, Object> configuration) {
    }

    /** 详情只在原字段配置上配入值，不生成另一套布局。 */
    record FieldView(String fieldName, Map<String, Object> configuration, Object value) {
    }
}
