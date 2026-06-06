package com.unique.examine.flow.service.impl;

import com.unique.examine.flow.entity.po.FlowTempVerSetting;
import com.unique.examine.flow.mapper.FlowTempVerSettingMapper;
import com.unique.examine.flow.service.IFlowTempVerSettingService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * temp_ver-全局设置（setting；异常兜底等） 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@Service
public class FlowTempVerSettingServiceImpl extends ServiceImpl<FlowTempVerSettingMapper, FlowTempVerSetting> implements IFlowTempVerSettingService {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-21
     * @param id 主键ID
     * @return data
     */
    @Override
    public FlowTempVerSetting queryById(Serializable id) {
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
    public void addOrUpdate(FlowTempVerSetting entity) {
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
    public BasePage<FlowTempVerSetting> queryPageList(PageEntity search) {
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
