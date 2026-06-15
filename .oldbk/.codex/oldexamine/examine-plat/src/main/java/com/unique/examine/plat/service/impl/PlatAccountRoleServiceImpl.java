package com.unique.examine.plat.service.impl;

import com.unique.examine.plat.entity.po.PlatAccountRole;
import com.unique.examine.plat.entity.po.PlatMenu;
import com.unique.examine.plat.mapper.PlatAccountRoleMapper;
import com.unique.examine.plat.service.IPlatAccountRoleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 平台账号与角色关联 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@Service
public class PlatAccountRoleServiceImpl extends ServiceImpl<PlatAccountRoleMapper, PlatAccountRole> implements IPlatAccountRoleService {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-10
     * @param id 主键ID
     * @return data
     */
    @Override
    public PlatAccountRole queryById(Serializable id) {
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
    public void addOrUpdate(PlatAccountRole entity) {
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
    public BasePage<PlatAccountRole> queryPageList(PageEntity search) {
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

    @Override
    public List<String> listRbacPermCodes(Long platAccountId) {
        return baseMapper.selectPermCodesByPlatAccountId(platAccountId);
    }

    @Override
    public List<PlatMenu> listRbacMenusByPlatAccount(Long platAccountId) {
        return baseMapper.selectMenusByPlatAccountId(platAccountId);
    }

    @Override
    public List<String> listRbacRoleCodes(Long platAccountId) {
        return baseMapper.selectRoleCodesByPlatAccountId(platAccountId);
    }
}
