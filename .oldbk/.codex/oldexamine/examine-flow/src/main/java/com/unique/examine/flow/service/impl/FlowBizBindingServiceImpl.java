package com.unique.examine.flow.service.impl;

import com.unique.examine.flow.entity.po.FlowBizBinding;
import com.unique.examine.flow.mapper.FlowBizBindingMapper;
import com.unique.examine.flow.service.IFlowBizBindingService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 业务与流程绑定 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@Service
public class FlowBizBindingServiceImpl extends ServiceImpl<FlowBizBindingMapper, FlowBizBinding> implements IFlowBizBindingService {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-10
     * @param id 主键ID
     * @return data
     */
    @Override
    public FlowBizBinding queryById(Serializable id) {
        return getById(id);
    }

    /**
     * 保存或新增信息
     * @author UNIQUE
     * @since 2026-04-10
     * @param entity entity
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addOrUpdate(FlowBizBinding entity) {
        saveOrUpdate(entity);
    }


    /**
     * 查询所有数据
     * @author UNIQUE
     * @since 2026-04-10
     * @param search 搜索条件
     * @return list
     */
    @Override
    public BasePage<FlowBizBinding> queryPageList(PageEntity search) {
        return lambdaQuery().page(search.parse());
    }

    /**
     * 根据ID列表删除数据
     * @author UNIQUE
     * @since 2026-04-10
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
