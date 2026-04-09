package com.unique.examine.plat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.plat.entity.PlatConfig;
import com.unique.examine.plat.mapper.PlatConfigMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlatConfigService extends ServiceImpl<PlatConfigMapper, PlatConfig> {

    public List<PlatConfig> listAll() {
        return list(new LambdaQueryWrapper<PlatConfig>().orderByAsc(PlatConfig::getGroupCode).orderByAsc(PlatConfig::getConfigKey));
    }

    public PlatConfig getByKey(String key) {
        return getOne(new LambdaQueryWrapper<PlatConfig>().eq(PlatConfig::getConfigKey, key));
    }

    public void saveOrUpdateConfig(PlatConfig c) {
        if (c.getConfigKey() == null || c.getConfigKey().isBlank()) {
            throw new BusinessException("configKey 不能为空");
        }
        if (c.getId() == null) {
            PlatConfig old = getByKey(c.getConfigKey());
            if (old != null) {
                throw new BusinessException("configKey 已存在");
            }
            save(c);
        } else {
            updateById(c);
        }
    }

    public void removeByKey(Long id) {
        removeById(id);
    }
}
