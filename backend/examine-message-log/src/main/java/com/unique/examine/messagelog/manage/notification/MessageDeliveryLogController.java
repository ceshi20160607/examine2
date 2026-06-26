package com.unique.examine.messagelog.manage.notification;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.messagelog.manage.notification.NotificationModels.MessageDeliveryLogQueryRequest;
import com.unique.examine.messagelog.manage.notification.NotificationModels.MessageDeliveryLogVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Message delivery log API controller.
 */
@RestController
public class MessageDeliveryLogController {

    private final MessageDeliveryLogService deliveryLogService;

    public MessageDeliveryLogController(MessageDeliveryLogService deliveryLogService) {
        this.deliveryLogService = deliveryLogService;
    }

    @GetMapping("/api/v1/systems/{systemId}/message-delivery-logs")
    public ApiResponse<PageResult<MessageDeliveryLogVO>> deliveryLogs(@PathVariable String systemId,
                                                                      @RequestParam(defaultValue = "1") int pageNo,
                                                                      @RequestParam(defaultValue = "20") int pageSize,
                                                                      MessageDeliveryLogQueryRequest query) {
        return ApiResponse.success(deliveryLogService.deliveryLogs(systemId, new PageRequest(pageNo, pageSize, null,
                List.of(), List.of()), query));
    }
}
