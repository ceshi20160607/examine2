package com.unique.examine.plat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.plat.entity.PlatSystem;
import com.unique.examine.plat.mapper.PlatSystemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlatSystemService extends ServiceImpl<PlatSystemMapper, PlatSystem> {

    public List<PlatSystem> listVisibleForUser(Long platId) {
        return list(new LambdaQueryWrapper<PlatSystem>()
                .eq(PlatSystem::getOwnerPlatAccountId, platId)
                .ne(PlatSystem::getId, 0L)
                .orderByDesc(PlatSystem::getCreateTime));
    }

    /** 含 id=0 平台占位，供管理查看 */
    public List<PlatSystem> listAllIncludingPlatform() {
        return list(new LambdaQueryWrapper<PlatSystem>().orderByAsc(PlatSystem::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public PlatSystem createSystem(Long platId, String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("系统名称不能为空");
        }
        PlatSystem s = new PlatSystem();
        s.setName(name.trim());
        s.setMultiTenantEnabled(0);
        s.setDefaultTenantId(0L);
        s.setStatus(1);
        s.setOwnerPlatAccountId(platId);
        save(s);
        return s;
    }

    public PlatSystem getRequired(Long id) {
        PlatSystem s = getById(id);
        if (s == null) {
            throw new BusinessException("系统不存在");
        }
        return s;
    }
}
