package com.unique.unexamine.platform.manage.foundation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.platform.base.entity.PlatformDefinition;
import com.unique.unexamine.platform.base.service.PlatformDefinitionBaseService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlatformDefinitionManager {
    public static final String DEFAULT_PLATFORM_CODE = "main";

    private final PlatformDefinitionBaseService platformService;

    public PlatformDefinitionManager(PlatformDefinitionBaseService platformService) {
        this.platformService = platformService;
    }

    public PlatformDefinition requireDefaultPlatform() {
        List<PlatformDefinition> existing = platformService.selectList(
                Wrappers.<PlatformDefinition>lambdaQuery()
                        .eq(PlatformDefinition::getCode, DEFAULT_PLATFORM_CODE));
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        PlatformDefinition platform = new PlatformDefinition();
        platform.setCode(DEFAULT_PLATFORM_CODE);
        platform.setName("Unexamine");
        platform.setSettingsJson("{}");
        platform.setStatus("ACTIVE");
        platformService.insert(platform);
        return platform;
    }
}
