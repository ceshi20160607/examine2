package com.unique.unexamine.print.base.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.unique.unexamine.print.base.entity.UnPrint;
import java.io.Serializable;
import java.util.Collection;

/** 打印模板的基础 CRUD 服务；业务权限和跨表规则由同模块 manage 层处理。 */
public interface IUnPrintService extends IService<UnPrint> {
    /** 按调用方限定的查询条件分页。 */
    Page<UnPrint> queryPageList(Page<UnPrint> page, Wrapper<UnPrint> query);

    /** 新增或更新当前表数据；更新目标不存在时明确失败。 */
    UnPrint addOrUpdate(UnPrint model);

    /** 按主键读取；不存在时返回 null 供业务服务判断。 */
    UnPrint queryById(Serializable id);

    /** 批量删除当前表数据。 */
    boolean deleteByIds(Collection<? extends Serializable> ids);
}
