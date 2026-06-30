package com.unique.examine.app.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.app.base.entity.SystemDataSource;
import com.unique.examine.app.base.mapper.SystemDataSourceMapper;
import com.unique.examine.app.base.service.SystemDataSourceBaseService;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SystemDataSourceBaseServiceImpl extends ServiceImpl<SystemDataSourceMapper, SystemDataSource>
        implements SystemDataSourceBaseService {

    @Override
    public Optional<SystemDataSource> findById(Long id) {
        return Optional.ofNullable(getById(id));
    }

    @Override
    public List<SystemDataSource> findAll() {
        return list();
    }

    @Override
    public SystemDataSource saveEntity(SystemDataSource entity) {
        saveOrUpdate(entity);
        return entity;
    }

    @Override
    public boolean deleteByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return removeBatchByIds(ids);
    }
}
