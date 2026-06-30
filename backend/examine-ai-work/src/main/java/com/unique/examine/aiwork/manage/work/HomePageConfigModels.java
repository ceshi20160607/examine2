package com.unique.examine.aiwork.manage.work;

import com.unique.examine.aiwork.manage.work.WorkManagementModels.PublishStateVO;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Home page configuration API models.
 */
public final class HomePageConfigModels {

    private HomePageConfigModels() {
    }

    public static class HomePageConfigUpdateRequest {
        private String title;
        private String subtitle;
        private String visualTone;
        private List<HomePageWidgetConfigVO> widgets;
        private String changeReason;

        public HomePageConfigUpdateRequest() {
        }

        public HomePageConfigUpdateRequest(String title, String subtitle, String visualTone,
                                           List<HomePageWidgetConfigVO> widgets, String changeReason) {
            this.title = title;
            this.subtitle = subtitle;
            this.visualTone = visualTone;
            this.widgets = widgets;
            this.changeReason = changeReason;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public void setSubtitle(String subtitle) {
            this.subtitle = subtitle;
        }

        public String subtitle() {
            return subtitle;
        }

        public String getVisualTone() {
            return visualTone;
        }

        public void setVisualTone(String visualTone) {
            this.visualTone = visualTone;
        }

        public String visualTone() {
            return visualTone;
        }

        public List<HomePageWidgetConfigVO> getWidgets() {
            return widgets;
        }

        public void setWidgets(List<HomePageWidgetConfigVO> widgets) {
            this.widgets = widgets;
        }

        public List<HomePageWidgetConfigVO> widgets() {
            return widgets;
        }

        public String getChangeReason() {
            return changeReason;
        }

        public void setChangeReason(String changeReason) {
            this.changeReason = changeReason;
        }

        public String changeReason() {
            return changeReason;
        }
    }

    public static class HomePageConfigVO {
        private String systemId;
        private String tenantId;
        private String title;
        private String subtitle;
        private String visualTone;
        private List<HomePageWidgetConfigVO> widgets;
        private PublishStateVO publishState;
        private LocalDateTime updatedAt;
        private String traceId;

        public HomePageConfigVO(String systemId, String tenantId, String title, String subtitle,
                                String visualTone, List<HomePageWidgetConfigVO> widgets,
                                PublishStateVO publishState, LocalDateTime updatedAt, String traceId) {
            this.systemId = systemId;
            this.tenantId = tenantId;
            this.title = title;
            this.subtitle = subtitle;
            this.visualTone = visualTone;
            this.widgets = widgets;
            this.publishState = publishState;
            this.updatedAt = updatedAt;
            this.traceId = traceId;
        }

        public String getSystemId() {
            return systemId;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getTitle() {
            return title;
        }

        public String title() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public String getVisualTone() {
            return visualTone;
        }

        public List<HomePageWidgetConfigVO> getWidgets() {
            return widgets;
        }

        public List<HomePageWidgetConfigVO> widgets() {
            return widgets;
        }

        public PublishStateVO getPublishState() {
            return publishState;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public String getTraceId() {
            return traceId;
        }
    }

    public static class HomePageWidgetConfigVO {
        private String widgetCode;
        private String widgetName;
        private String widgetType;
        private String sourceType;
        private Integer sort;
        private Boolean visible;

        public HomePageWidgetConfigVO() {
        }

        public HomePageWidgetConfigVO(String widgetCode, String widgetName, String widgetType,
                                      String sourceType, Integer sort, Boolean visible) {
            this.widgetCode = widgetCode;
            this.widgetName = widgetName;
            this.widgetType = widgetType;
            this.sourceType = sourceType;
            this.sort = sort;
            this.visible = visible;
        }

        public String getWidgetCode() {
            return widgetCode;
        }

        public void setWidgetCode(String widgetCode) {
            this.widgetCode = widgetCode;
        }

        public String widgetCode() {
            return widgetCode;
        }

        public String getWidgetName() {
            return widgetName;
        }

        public void setWidgetName(String widgetName) {
            this.widgetName = widgetName;
        }

        public String getWidgetType() {
            return widgetType;
        }

        public void setWidgetType(String widgetType) {
            this.widgetType = widgetType;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public Integer getSort() {
            return sort;
        }

        public void setSort(Integer sort) {
            this.sort = sort;
        }

        public Boolean getVisible() {
            return visible;
        }

        public void setVisible(Boolean visible) {
            this.visible = visible;
        }

        public Boolean visible() {
            return visible;
        }
    }
}
