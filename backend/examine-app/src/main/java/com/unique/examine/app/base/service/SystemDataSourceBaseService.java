package com.unique.examine.app.base.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.unique.examine.app.base.entity.SystemDataSource;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SystemDataSourceBaseService extends IService<SystemDataSource> {

    Optional<SystemDataSource> findById(Long id);

    List<SystemDataSource> findAll();

    SystemDataSource saveEntity(SystemDataSource entity);

    boolean deleteByIds(Collection<Long> ids);
}
