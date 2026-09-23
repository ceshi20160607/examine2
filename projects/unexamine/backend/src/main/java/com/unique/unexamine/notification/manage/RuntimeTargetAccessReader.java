package com.unique.unexamine.notification.manage;

import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordList;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知域会把运行时目标的“无权读取”转换为失效状态或不可打开标记，因此目标读取事务必须隔离。
 * 被拒绝的运行时读取只结束自己的事务，不能把待办状态写入或消息列表的外层事务标记为回滚。
 */
@Service
public class RuntimeTargetAccessReader {
    private final RuntimeDataService runtimeDataService;

    public RuntimeTargetAccessReader(RuntimeDataService runtimeDataService) {
        this.runtimeDataService = runtimeDataService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RuntimeRecordList list(
            AuthenticatedContext context, String moduleCode, String filtersJson, int page, String traceId) {
        return runtimeDataService.list(context, moduleCode, "ACTIVE", "OWN", "", filtersJson,
                "updatedAt", "DESC", page, 200, traceId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RuntimeRecordView detail(
            AuthenticatedContext context, String moduleCode, Long recordId, String traceId) {
        return runtimeDataService.detail(context, moduleCode, recordId, traceId);
    }
}
