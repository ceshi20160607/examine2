package com.unique.examine.analytics.port;

import com.unique.examine.analytics.domain.AnalyticsActor;
import com.unique.examine.analytics.domain.AnalyticsRange;
import com.unique.examine.analytics.domain.OperationsDashboard;

public interface FlowAnalyticsSource {
    OperationsDashboard.FlowSection load(AnalyticsActor actor, AnalyticsRange range);
}
