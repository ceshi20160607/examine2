package com.unique.unexamine.sys.module.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import java.util.List;

/** 模块字段的基础服务；业务筛选与字段展示由 manage 负责。 */
public interface IUnModuleFieldService extends IService<UnModuleField> {
    /** 根据公共查询对象分页；具体业务可扩展查询对象并在 manage 实现条件。 */
    Page<UnModuleField> queryPageList(SearchBO search);

    /** 保存主表数据。 */
    UnModuleField addOrUpdate(UnModuleField model);

    /** 根据主键读取未删除的记录。 */
    UnModuleField queryById(Long id);

    /** 按主键批量逻辑删除记录。 */
    boolean deleteByIds(List<Long> ids);

    /** 读取当前模块全部历史字段键，供预留主表槽位分配使用。 */
    List<String> allFieldKeysForStorage(Long moduleId);
}
