package com.unique.examine.flow.service.impl;

import com.unique.examine.flow.entity.po.FlowFormTemplate;
import com.unique.examine.flow.mapper.FlowFormTemplateMapper;
import com.unique.examine.flow.service.IFlowFormTemplateService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 表单模板（发起/审批） 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@Service
public class FlowFormTemplateServiceImpl extends ServiceImpl<FlowFormTemplateMapper, FlowFormTemplate> implements IFlowFormTemplateService {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-10
     * @param id 主键ID
     * @return data
     */
    @Override
    public FlowFormTemplate queryById(Serializable id) {
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
    public void addOrUpdate(FlowFormTemplate entity) {
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
    public BasePage<FlowFormTemplate> queryPageList(PageEntity search) {
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
