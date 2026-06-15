package com.unique.examine.module.base.service.impl;

import com.unique.examine.module.base.entity.RoleFieldPermission;
import com.unique.examine.module.base.mapper.RoleFieldPermissionMapper;
import com.unique.examine.module.base.service.IRoleFieldPermissionService;
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
 * 字段可见、可写、导出明文和 OpenAPI 读写授权。 基础 CRUD 服务实现。
 *
 * @author examine-generator
 * @since generated
 */
@Service
public class RoleFieldPermissionServiceImpl extends ServiceImpl<RoleFieldPermissionMapper, RoleFieldPermission>
        implements IRoleFieldPermissionService {

    /**
     * 根据主键查询单条数据。
     *
     * @param id 主键 ID
     * @return 表数据
     */
    @Override
    public RoleFieldPermission queryById(Serializable id) {
        return getById(id);
    }

    /**
     * 查询全部数据。
     *
     * @return 表数据列表
     */
    @Override
    public List<RoleFieldPermission> queryAll() {
        return list();
    }

    /**
     * 分页查询数据。
     *
     * @param page MyBatis-Plus 分页对象
     * @return 分页结果
     */
    @Override
    public IPage<RoleFieldPermission> queryPage(Page<RoleFieldPermission> page) {
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
    public boolean addOrUpdate(RoleFieldPermission entity) {
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
