package com.unique.examine.flow.service;

import com.unique.examine.flow.entity.po.FlowBizBinding;
import com.baomidou.mybatisplus.extension.service.IService;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 业务与流程绑定 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
public interface IFlowBizBindingService extends IService<FlowBizBinding> {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-10
     * @param id 主键ID
     * @return data
     */
    public FlowBizBinding queryById(Serializable id);

    /**
     * 保存或新增信息
     * @author UNIQUE
     * @since 2026-04-10
     * @param entity entity
     */
    public void addOrUpdate(FlowBizBinding entity);


    /**
     * 查询所有数据
     * @author UNIQUE
     * @since 2026-04-10
     * @param search 搜索条件
     * @return list
     */
    public BasePage<FlowBizBinding> queryPageList(PageEntity search);

    /**
     * 根据ID列表删除数据
     * @author UNIQUE
     * @since 2026-04-10
     * @param ids ids
     */
    public void deleteByIds(List<Serializable> ids);
}
