package com.unique.examine.flow.service.impl;

import com.unique.examine.flow.entity.po.FlowTaskActor;
import com.unique.examine.flow.mapper.FlowTaskActorMapper;
import com.unique.examine.flow.service.IFlowTaskActorService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 任务参与人（会签/候选/抄送） 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@Service
public class FlowTaskActorServiceImpl extends ServiceImpl<FlowTaskActorMapper, FlowTaskActor> implements IFlowTaskActorService {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-21
     * @param id 主键ID
     * @return data
     */
    @Override
    public FlowTaskActor queryById(Serializable id) {
        return getById(id);
    }

    /**
     * 保存或新增信息
     * @author UNIQUE
     * @since 2026-04-21
     * @param entity entity
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addOrUpdate(FlowTaskActor entity) {
        saveOrUpdate(entity);
    }


    /**
     * 查询所有数据
     * @author UNIQUE
     * @since 2026-04-21
     * @param search 搜索条件
     * @return list
     */
    @Override
    public BasePage<FlowTaskActor> queryPageList(PageEntity search) {
        return lambdaQuery().page(search.parse());
    }

    /**
     * 根据ID列表删除数据
     * @author UNIQUE
     * @since 2026-04-21
     * @param ids ids
     */
    @Override
    public void deleteByIds(List<Serializable> ids) {
        if (ids == null || ids.isEmpty()) {
              return;
        }
        removeByIds(ids);
    }
}
