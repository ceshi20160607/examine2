package com.unique.examine.plat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;
import com.unique.examine.plat.entity.po.PlatAccountRole;
import com.unique.examine.plat.entity.po.PlatMenu;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 平台账号与角色关联 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
public interface IPlatAccountRoleService extends IService<PlatAccountRole> {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-10
     * @param id 主键ID
     * @return data
     */
    public PlatAccountRole queryById(Serializable id);

    /**
     * 保存或新增信息
     * @author UNIQUE
     * @since 2026-04-10
     * @param entity entity
     */
    public void addOrUpdate(PlatAccountRole entity);


    /**
     * 查询所有数据
     * @author UNIQUE
     * @since 2026-04-10
     * @param search 搜索条件
     * @return list
     */
    public BasePage<PlatAccountRole> queryPageList(PageEntity search);

    /**
     * 根据ID列表删除数据
     * @author UNIQUE
     * @since 2026-04-10
     * @param ids ids
     */
    public void deleteByIds(List<Serializable> ids);

    /** RBAC：账号经角色-菜单解析出的权限码（多表聚合） */
    List<String> listRbacPermCodes(Long platAccountId);

    /** RBAC：账号可见菜单行 */
    List<PlatMenu> listRbacMenusByPlatAccount(Long platAccountId);

    /** RBAC：账号绑定的角色编码 */
    List<String> listRbacRoleCodes(Long platAccountId);
}
