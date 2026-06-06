package com.unique.examine.flow.base.service.impl;

import com.unique.examine.flow.base.entity.TraceLog;
import com.unique.examine.flow.base.mapper.TraceLogMapper;
import com.unique.examine.flow.base.service.ITraceLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 流程推进轨迹日志。 基础 CRUD 服务实现。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Service
public class TraceLogServiceImpl extends ServiceImpl<TraceLogMapper, TraceLog>
        implements ITraceLogService {

    /**
     * 根据主键查询单条数据。
     *
     * @param id 主键 ID
     * @return 表数据
     */
    @Override
    public TraceLog queryById(Serializable id) {
        return getById(id);
    }

    /**
     * 查询全部数据。
     *
     * @return 表数据列表
     */
    @Override
    public List<TraceLog> queryAll() {
        return list();
    }

    /**
     * 分页查询数据。
     *
     * @param page MyBatis-Plus 分页对象
     * @return 分页结果
     */
    @Override
    public IPage<TraceLog> queryPage(Page<TraceLog> page) {
        return this.page(page);
    }

    /**
     * 新增或更新数据。
     *
     * @param entity 表实体
     * @return 是否保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addOrUpdate(TraceLog entity) {
        return saveOrUpdate(entity);
    }

    /**
     * 根据主键集合批量删除数据。
     *
     * @param ids 主键集合
     * @return 是否删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByIds(Collection<? extends Serializable> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return false;
        }
        return removeByIds(ids);
    }
}
