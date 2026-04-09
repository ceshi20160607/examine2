package com.unique.examine.plat.service.impl;

import com.unique.examine.plat.entity.PO.PlatTenant;
import com.unique.examine.plat.mapper.PlatTenantMapper;
import com.unique.examine.plat.service.IPlatTenantService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 租户 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-09
 */
@Service
public class PlatTenantServiceImpl extends ServiceImpl<PlatTenantMapper, PlatTenant> implements IPlatTenantService {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-09
     * @param id 主键ID
     * @return data
     */
    @Override
    public PlatTenant queryById(Serializable id) {
        return getById(id);
    }

    /**
     * 保存或新增信息
     * @author UNIQUE
     * @since 2026-04-09
     * @param entity entity
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addOrUpdate(PlatTenant entity) {
        saveOrUpdate(entity);
    }


    /**
     * 查询所有数据
     * @author UNIQUE
     * @since 2026-04-09
     * @param search 搜索条件
     * @return list
     */
    @Override
    public BasePage<PlatTenant> queryPageList(PageEntity search) {
        return lambdaQuery().page(search.parse());
    }

    /**
     * 根据ID列表删除数据
     * @author UNIQUE
     * @since 2026-04-09
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
