package com.unique.examine.flow.service;

import com.unique.examine.flow.entity.PO.FlowTaskActor;
import com.baomidou.mybatisplus.extension.service.IService;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 任务参与人（会签/候选/抄送） 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-09
 */
public interface IFlowTaskActorService extends IService<FlowTaskActor> {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-09
     * @param id 主键ID
     * @return data
     */
    public FlowTaskActor queryById(Serializable id);

    /**
     * 保存或新增信息
     * @author UNIQUE
     * @since 2026-04-09
     * @param entity entity
     */
    public void addOrUpdate(FlowTaskActor entity);


    /**
     * 查询所有数据
     * @author UNIQUE
     * @since 2026-04-09
     * @param search 搜索条件
     * @return list
     */
    public BasePage<FlowTaskActor> queryPageList(PageEntity search);

    /**
     * 根据ID列表删除数据
     * @author UNIQUE
     * @since 2026-04-09
     * @param ids ids
     */
    public void deleteByIds(List<Serializable> ids);
}
