package com.unique.examine.flow.service;

import com.unique.examine.flow.entity.po.FlowTempVerLineCond;
import com.baomidou.mybatisplus.extension.service.IService;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * temp_ver-连线条件（line_cond；关系表版） 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
public interface IFlowTempVerLineCondService extends IService<FlowTempVerLineCond> {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-21
     * @param id 主键ID
     * @return data
     */
    public FlowTempVerLineCond queryById(Serializable id);

    /**
     * 保存或新增信息
     * @author UNIQUE
     * @since 2026-04-21
     * @param entity entity
     */
    public void addOrUpdate(FlowTempVerLineCond entity);


    /**
     * 查询所有数据
     * @author UNIQUE
     * @since 2026-04-21
     * @param search 搜索条件
     * @return list
     */
    public BasePage<FlowTempVerLineCond> queryPageList(PageEntity search);

    /**
     * 根据ID列表删除数据
     * @author UNIQUE
     * @since 2026-04-21
     * @param ids ids
     */
    public void deleteByIds(List<Serializable> ids);
}
