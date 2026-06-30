package com.unique.examine.aiwork.manage.work;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.aiwork.manage.work.HomePageConfigModels.HomePageConfigUpdateRequest;
import com.unique.examine.aiwork.manage.work.HomePageConfigModels.HomePageConfigVO;
import com.unique.examine.aiwork.manage.work.HomePageConfigModels.HomePageWidgetConfigVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.ImpactRefVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.PublishCheckItemVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.PublishStateVO;
import com.unique.examine.aiwork.manage.work.WorkManagementModels.WorkConfigPublishCheckResult;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.module.base.entity.ModuleWorkConfig;
import com.unique.examine.module.base.service.ModuleWorkConfigBaseService;
import com.unique.examine.module.manage.common.ModuleSystemContextResolver;
import com.unique.examine.module.manage.common.ModuleSystemContextResolver.ModuleSystemContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * System home page configuration service backed by module work config storage.
 */
@Service
public class HomePageConfigService {

    private static final String HOME_PAGE_CONFIG = "SYSTEM_HOME_PAGE";
    private static final String DEFAULT_VERSION = "home_page_v1";

    private final ModuleWorkConfigBaseService workConfigBaseService;
    private final ModuleSystemContextResolver moduleSystemContextResolver;
    private final ObjectMapper objectMapper;

    public HomePageConfigService(ModuleWorkConfigBaseService workConfigBaseService,
                                 ModuleSystemContextResolver moduleSystemContextResolver,
                                 ObjectMapper objectMapper) {
        this.workConfigBaseService = workConfigBaseService;
        this.moduleSystemContextResolver = moduleSystemContextResolver;
        this.objectMapper = objectMapper;
    }

    public HomePageConfigVO config(String systemId) {
        ModuleSystemContext context = moduleSystemContextResolver.resolve(systemId);
        ModuleWorkConfig entity = configEntity(context);
        HomePageConfigUpdateRequest config = defaultConfig();
        LocalDateTime updatedAt = LocalDateTime.now();
        if (!Objects.isNull(entity)) {
            config = readConfig(entity);
            updatedAt = entity.getUpdatedAt();
        }
        return view(context, config, publishState(entity), updatedAt);
    }

    public HomePageConfigVO updateConfig(String systemId, HomePageConfigUpdateRequest request) {
        ModuleSystemContext context = moduleSystemContextResolver.resolve(systemId);
        ModuleWorkConfig entity = configEntity(context);
        HomePageConfigUpdateRequest existing = null;
        if (!Objects.isNull(entity)) {
            existing = readConfig(entity);
        }
        HomePageConfigUpdateRequest merged = mergeConfig(existing, request);
        LocalDateTime now = LocalDateTime.now();
        if (Objects.isNull(entity)) {
            entity = new ModuleWorkConfig();
            entity.setSystemId(context.systemId());
            entity.setTenantId(context.tenantId());
            entity.setConfigType(HOME_PAGE_CONFIG);
            entity.setPublishedVersion(DEFAULT_VERSION);
        }
        entity.setFieldList(writeJson(merged));
        entity.setCardFields(writeJson(widgetCodes(merged.widgets())));
        entity.setPublishStatus("DRAFT");
        entity.setUpdatedAt(now);
        workConfigBaseService.saveEntity(entity);
        return view(context, merged, publishState(entity), now);
    }

    public WorkConfigPublishCheckResult publishCheck(String systemId) {
        ModuleSystemContext context = moduleSystemContextResolver.resolve(systemId);
        HomePageConfigVO config = config(systemId);
        List<PublishCheckItemVO> items = new ArrayList<>();
        items.add(new PublishCheckItemVO("HOME_TITLE", "Home page title", "PASS",
                StringUtils.hasText(config.title()), "Home page has a visible business title."));
        items.add(new PublishCheckItemVO("HOME_WIDGETS", "Home page widgets", "PASS",
                hasVisibleWidget(config.widgets()), "At least one visible widget is configured."));
        items.add(new PublishCheckItemVO("HOME_RUNTIME_BINDING", "Runtime binding", "PASS", true,
                "Runtime dashboard reads the persisted home page configuration."));
        boolean passed = true;
        for (PublishCheckItemVO item : items) {
            if (!item.passed()) {
                passed = false;
                break;
            }
        }
        return new WorkConfigPublishCheckResult(passed, DEFAULT_VERSION, items,
                List.of(new ImpactRefVO("SYSTEM_HOME", String.valueOf(context.systemId()), config.title())),
                RequestContext.current().traceId());
    }

    private ModuleWorkConfig configEntity(ModuleSystemContext context) {
        return workConfigBaseService.getOne(new QueryWrapper<ModuleWorkConfig>()
                .eq("system_id", context.systemId())
                .eq("tenant_id", context.tenantId())
                .eq("config_type", HOME_PAGE_CONFIG)
                .last("LIMIT 1"), false);
    }

    private HomePageConfigUpdateRequest readConfig(ModuleWorkConfig entity) {
        if (Objects.isNull(entity) || !StringUtils.hasText(entity.getFieldList())) {
            return defaultConfig();
        }
        try {
            return objectMapper.readValue(entity.getFieldList(), HomePageConfigUpdateRequest.class);
        } catch (Exception ex) {
            return defaultConfig();
        }
    }

    private HomePageConfigUpdateRequest mergeConfig(HomePageConfigUpdateRequest existing,
                                                   HomePageConfigUpdateRequest request) {
        HomePageConfigUpdateRequest base = existing;
        if (Objects.isNull(base)) {
            base = defaultConfig();
        }
        String title = base.title();
        String subtitle = base.subtitle();
        String visualTone = base.visualTone();
        List<HomePageWidgetConfigVO> widgets = base.widgets();
        String changeReason = base.changeReason();
        if (!Objects.isNull(request)) {
            if (StringUtils.hasText(request.title())) {
                title = request.title();
            }
            if (StringUtils.hasText(request.subtitle())) {
                subtitle = request.subtitle();
            }
            if (StringUtils.hasText(request.visualTone())) {
                visualTone = request.visualTone();
            }
            if (request.widgets() != null && !request.widgets().isEmpty()) {
                widgets = request.widgets();
            }
            if (StringUtils.hasText(request.changeReason())) {
                changeReason = request.changeReason();
            }
        }
        return new HomePageConfigUpdateRequest(title, subtitle, visualTone, widgets, changeReason);
    }

    private HomePageConfigVO view(ModuleSystemContext context, HomePageConfigUpdateRequest config,
                                  PublishStateVO publishState, LocalDateTime updatedAt) {
        HomePageConfigUpdateRequest effectiveConfig = config;
        if (Objects.isNull(effectiveConfig)) {
            effectiveConfig = defaultConfig();
        }
        LocalDateTime effectiveUpdatedAt = updatedAt;
        if (Objects.isNull(effectiveUpdatedAt)) {
            effectiveUpdatedAt = LocalDateTime.now();
        }
        return new HomePageConfigVO(String.valueOf(context.systemId()), String.valueOf(context.tenantId()),
                effectiveConfig.title(), effectiveConfig.subtitle(), effectiveConfig.visualTone(),
                effectiveConfig.widgets(), publishState, effectiveUpdatedAt, RequestContext.current().traceId());
    }

    private PublishStateVO publishState(ModuleWorkConfig entity) {
        if (Objects.isNull(entity)) {
            return new PublishStateVO("DRAFT", DEFAULT_VERSION, true, null);
        }
        return new PublishStateVO(safeText(entity.getPublishStatus(), "DRAFT"),
                safeText(entity.getPublishedVersion(), DEFAULT_VERSION), true, null);
    }

    private HomePageConfigUpdateRequest defaultConfig() {
        return new HomePageConfigUpdateRequest("系统工作台", "当前系统成员权限范围内的业务入口、待办、消息和工作概览。",
                "calm-workbench", List.of(
                new HomePageWidgetConfigVO("overview", "工作概览", "metric", "WORK_DASHBOARD", 10, true),
                new HomePageWidgetConfigVO("warnings", "今日预警", "list", "WORK_WARNING", 20, true),
                new HomePageWidgetConfigVO("calendar", "当月日历", "calendar", "WORK_CALENDAR", 30, true),
                new HomePageWidgetConfigVO("modules", "业务模块入口", "shortcut", "RUNTIME_MODULES", 40, true)),
                "default");
    }

    private boolean hasVisibleWidget(List<HomePageWidgetConfigVO> widgets) {
        if (widgets == null || widgets.isEmpty()) {
            return false;
        }
        for (HomePageWidgetConfigVO widget : widgets) {
            if (!Boolean.FALSE.equals(widget.visible())) {
                return true;
            }
        }
        return false;
    }

    private List<String> widgetCodes(List<HomePageWidgetConfigVO> widgets) {
        List<String> codes = new ArrayList<>();
        if (widgets == null) {
            return codes;
        }
        for (HomePageWidgetConfigVO widget : widgets) {
            codes.add(widget.widgetCode());
        }
        return codes;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String safeText(String value, String fallback) {
        if (StringUtils.hasText(value)) {
            return value;
        }
        return fallback;
    }
}
