package com.unique.examine.flow.service.impl;

import com.unique.examine.flow.entity.po.FlowRecordNode;
import com.unique.examine.flow.mapper.FlowRecordNodeMapper;
import com.unique.examine.flow.service.IFlowRecordNodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * record-节点（node；关系表版） 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@Service
public class FlowRecordNodeServiceImpl extends ServiceImpl<FlowRecordNodeMapper, FlowRecordNode> implements IFlowRecordNodeService {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-21
     * @param id 主键ID
     * @return data
     */
    @Override
    public FlowRecordNode queryById(Serializable id) {
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
    public void addOrUpdate(FlowRecordNode entity) {
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
    public BasePage<FlowRecordNode> queryPageList(PageEntity search) {
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
