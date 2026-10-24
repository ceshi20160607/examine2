package com.unique.unexamine.plat.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.plat.base.entity.UnPlatRoleData;
import java.util.List;

/** 平台角色数据权限的基础服务；业务筛选与字段展示由 manage 负责。 */
public interface IUnPlatRoleDataService extends IService<UnPlatRoleData> {
    /** 根据公共查询对象分页；具体业务可扩展查询对象并在 manage 实现条件。 */
    Page<UnPlatRoleData> queryPageList(SearchBO search);

    /** 保存主表数据。 */
    UnPlatRoleData addOrUpdate(UnPlatRoleData model);

    /** 根据主键读取未删除的记录。 */
    UnPlatRoleData queryById(Long id);

    /** 按主键批量逻辑删除记录。 */
    boolean deleteByIds(List<Long> ids);
}
