package com.unique.examine.plat.manage;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.plat.entity.po.PlatAccount;
import com.unique.examine.plat.manage.PlatRbacManageService;
import com.unique.examine.plat.service.IPlatAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PlatPermissionManageService {

    @Autowired
    private IPlatAccountService platAccountService;
    @Autowired
    private PlatRbacManageService platRbacManageService;

    public PlatAccount requireAccount(Long platId) {
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        PlatAccount acc = platAccountService.getById(platId);
        if (acc == null) {
            throw new BusinessException(401, "账号不存在");
        }
        return acc;
    }

    public void requirePermission(Long platId, String permCode) {
        requireAccount(platId);
        if (!hasPermission(platId, permCode)) {
            throw new BusinessException(403, "无平台权限: " + permCode);
        }
    }

    public boolean hasPermission(Long platId, String permCode) {
        PlatAccount acc = platAccountService.getById(platId);
        if (acc == null || permCode == null || permCode.isBlank()) {
            return false;
        }
        return platRbacManageService.resolveEffectivePermCodes(platId, null).contains(permCode.trim());
    }
}
