package com.unique.unexamine.runtimedata.manage;

import com.unique.unexamine.runtimedata.base.entity.BizTenantShareUsage;
import com.unique.unexamine.runtimedata.base.service.BizTenantShareUsageBaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class TenantShareUsageRecorder {
    private final BizTenantShareUsageBaseService usageService;

    public TenantShareUsageRecorder(BizTenantShareUsageBaseService usageService) {
        this.usageService = usageService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long shareId, Long targetTenantMemberId, String actionCode, String resultCode) {
        BizTenantShareUsage usage = new BizTenantShareUsage();
        usage.setShareId(shareId);
        usage.setTargetTenantMemberId(targetTenantMemberId);
        usage.setActionCode(actionCode);
        usage.setResultCode(resultCode);
        usage.setOccurredAt(LocalDateTime.now());
        usageService.insert(usage);
    }
}
