package com.unique.examine.app.service;

import com.unique.examine.app.entity.po.AppClientCredential;
import com.baomidou.mybatisplus.extension.service.IService;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * client 凭证（AK/SK） 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
public interface IAppClientCredentialService extends IService<AppClientCredential> {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-10
     * @param id 主键ID
     * @return data
     */
    public AppClientCredential queryById(Serializable id);

    /**
     * 保存或新增信息
     * @author UNIQUE
     * @since 2026-04-10
     * @param entity entity
     */
    public void addOrUpdate(AppClientCredential entity);


    /**
     * 查询所有数据
     * @author UNIQUE
     * @since 2026-04-10
     * @param search 搜索条件
     * @return list
     */
    public BasePage<AppClientCredential> queryPageList(PageEntity search);

    /**
     * 根据ID列表删除数据
     * @author UNIQUE
     * @since 2026-04-10
     * @param ids ids
     */
    public void deleteByIds(List<Serializable> ids);
}
