package com.unique.examine.upload.service;

import com.unique.examine.upload.entity.PO.UploadFile;
import com.baomidou.mybatisplus.extension.service.IService;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 上传文件主表 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-09
 */
public interface IUploadFileService extends IService<UploadFile> {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-09
     * @param id 主键ID
     * @return data
     */
    public UploadFile queryById(Serializable id);

    /**
     * 保存或新增信息
     * @author UNIQUE
     * @since 2026-04-09
     * @param entity entity
     */
    public void addOrUpdate(UploadFile entity);


    /**
     * 查询所有数据
     * @author UNIQUE
     * @since 2026-04-09
     * @param search 搜索条件
     * @return list
     */
    public BasePage<UploadFile> queryPageList(PageEntity search);

    /**
     * 根据ID列表删除数据
     * @author UNIQUE
     * @since 2026-04-09
     * @param ids ids
     */
    public void deleteByIds(List<Serializable> ids);
}
