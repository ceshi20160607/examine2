package com.unique.unexamine.print.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.print.base.entity.UnPrint;
import com.unique.unexamine.print.base.mapper.UnPrintMapper;
import com.unique.unexamine.print.base.service.IUnPrintService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.Serializable;
import java.util.Collection;

/** 打印模板的基础 CRUD 实现；只操作本表，不处理业务授权。 */
@Service
public class UnPrintServiceImpl extends ServiceImpl<UnPrintMapper, UnPrint> implements IUnPrintService {
    /** 分页查询在 Service 内执行，Controller 不直接构造数据库调用。 */
    @Override
    public Page<UnPrint> queryPageList(Page<UnPrint> page, Wrapper<UnPrint> query) {
        return page(page, query);
    }

    /** 基础保存只依据主键区分新增和更新，额外校验由业务服务完成。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnPrint addOrUpdate(UnPrint model) {
        if (model.getId() == null) {
            if (!save(model)) {
                throw new IllegalStateException("新增记录失败");
            }
        } else if (!updateById(model)) {
            throw new IllegalArgumentException("待更新记录不存在");
        }
        return model;
    }

    /** 不存在时保留空值，由上层映射业务错误。 */
    @Override
    public UnPrint queryById(Serializable id) {
        return getById(id);
    }

    /** 批量删除由 Service 持有事务边界。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByIds(Collection<? extends Serializable> ids) {
        return removeByIds(ids);
    }
}
