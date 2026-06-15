package com.unique.examine.module.base.service;

import com.unique.examine.module.base.entity.RecordHistory;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 记录变更、状态、附件和发布版本历史快照。 基础 CRUD 服务。
 *
 * @author examine-generator
 * @since generated
 */
public interface IRecordHistoryService extends IService<RecordHistory> {

    /**
     * 根据主键查询单条数据。
     *
     * @param id 主键 ID
     * @return 表数据
     */
    RecordHistory queryById(Serializable id);

    /**
     * 查询全部数据。
     *
     * @return 表数据列表
     */
    List<RecordHistory> queryAll();

    /**
     * 分页查询数据。
     *
     * @param page MyBatis-Plus 分页对象
     * @return 分页结果
     */
    IPage<RecordHistory> queryPage(Page<RecordHistory> page);

    /**
     * 新增或更新数据。
     *
     * @param entity 表实体
     * @return 是否保存成功
     */
    boolean addOrUpdate(RecordHistory entity);

    /**
     * 根据主键集合批量删除数据。
     *
     * @param ids 主键集合
     * @return 是否删除成功
     */
    boolean deleteByIds(Collection<? extends Serializable> ids);
}
