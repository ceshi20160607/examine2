package com.unique.unexamine.dashboard.base.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.dashboard.base.entity.UnDashboardUser;
import com.unique.unexamine.dashboard.base.mapper.UnDashboardUserMapper;
import com.unique.unexamine.dashboard.base.service.IUnDashboardUserService;
import java.util.List;
import org.springframework.stereotype.Service;

/** 仪表盘人员与组织访问范围的基础 CRUD 实现；业务条件由 manage 传给 MyBatis-Plus。 */
@Service
public class UnDashboardUserServiceImpl extends ServiceImpl<UnDashboardUserMapper, UnDashboardUser>
        implements IUnDashboardUserService {
    /** 公共查询对象负责分页，基础实现仅按主键稳定排序。 */
    @Override
    public Page<UnDashboardUser> queryPageList(SearchBO search) {
        return page(search.parse(),
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UnDashboardUser>().orderByDesc("id"));
    }

    /** 使用 MyBatis-Plus 原生保存与更新能力。 */
    @Override
    public UnDashboardUser addOrUpdate(UnDashboardUser model) {
        saveOrUpdate(model);
        return model;
    }

    /** 基础读取只负责主键定位；调用方决定业务可见范围。 */
    @Override
    public UnDashboardUser queryById(Long id) {
        UnDashboardUser model = getById(id);
        if (model == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "记录不存在: " + id);
        return model;
    }

    /** 逻辑删除规则由实体与 MyBatis-Plus 统一处理。 */
    @Override
    public boolean deleteByIds(List<Long> ids) {
        return removeByIds(ids);
    }
}
