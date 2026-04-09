package com.unique.examine.app.service.impl;

import com.unique.examine.app.entity.PO.AppClientScope;
import com.unique.examine.app.mapper.AppClientScopeMapper;
import com.unique.examine.app.service.IAppClientScopeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * client 授权范围 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-09
 */
@Service
public class AppClientScopeServiceImpl extends ServiceImpl<AppClientScopeMapper, AppClientScope> implements IAppClientScopeService {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-09
     * @param id 主键ID
     * @return data
     */
    @Override
    public AppClientScope queryById(Serializable id) {
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
    public void addOrUpdate(AppClientScope entity) {
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
    public BasePage<AppClientScope> queryPageList(PageEntity search) {
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
