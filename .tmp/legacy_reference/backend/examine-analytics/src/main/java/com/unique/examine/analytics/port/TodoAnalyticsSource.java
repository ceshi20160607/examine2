package com.unique.examine.analytics.port;

import com.unique.examine.analytics.domain.AnalyticsActor;
import com.unique.examine.analytics.domain.AnalyticsRange;
import com.unique.examine.analytics.domain.OperationsDashboard;

public interface TodoAnalyticsSource {
    OperationsDashboard.TodoSection load(AnalyticsActor actor, AnalyticsRange range);
}
