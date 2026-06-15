package com.unique.examine.flow.service;

import com.unique.examine.flow.entity.po.FlowTempVerNodeSetting;
import com.baomidou.mybatisplus.extension.service.IService;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * temp_ver-节点设置（setting；异常兜底覆盖） 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
public interface IFlowTempVerNodeSettingService extends IService<FlowTempVerNodeSetting> {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-21
     * @param id 主键ID
     * @return data
     */
    public FlowTempVerNodeSetting queryById(Serializable id);

    /**
     * 保存或新增信息
     * @author UNIQUE
     * @since 2026-04-21
     * @param entity entity
     */
    public void addOrUpdate(FlowTempVerNodeSetting entity);


    /**
     * 查询所有数据
     * @author UNIQUE
     * @since 2026-04-21
     * @param search 搜索条件
     * @return list
     */
    public BasePage<FlowTempVerNodeSetting> queryPageList(PageEntity search);

    /**
     * 根据ID列表删除数据
     * @author UNIQUE
     * @since 2026-04-21
     * @param ids ids
     */
    public void deleteByIds(List<Serializable> ids);
}
