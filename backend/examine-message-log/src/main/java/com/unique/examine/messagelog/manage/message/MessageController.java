package com.unique.examine.messagelog.manage.message;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.messagelog.manage.message.MessageModels.MessageActionResult;
import com.unique.examine.messagelog.manage.message.MessageModels.MessageBulkActionRequest;
import com.unique.examine.messagelog.manage.message.MessageModels.MessageCardVO;
import com.unique.examine.messagelog.manage.message.MessageModels.MessageLoadMoreRequest;
import com.unique.examine.messagelog.manage.message.MessageModels.MessageLoadMoreResult;
import com.unique.examine.messagelog.manage.message.MessageModels.MessageMarkAllReadRequest;
import com.unique.examine.messagelog.manage.message.MessageModels.MessageQueryRequest;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Message center API controller.
 */
@RestController
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/api/v1/platform/messages/search")
    public ApiResponse<PageResult<MessageCardVO>> platformMessages(@RequestParam(defaultValue = "1") int pageNo,
                                                                   @RequestParam(defaultValue = "20") int pageSize,
                                                                   @RequestBody MessageQueryRequest query) {
        return ApiResponse.success(messageService.platformMessages(new PageRequest(pageNo, pageSize, null,
                List.of(), List.of()), query));
    }

    @PostMapping("/api/v1/platform/messages/load-more")
    public ApiResponse<MessageLoadMoreResult> loadMorePlatform(@RequestBody MessageLoadMoreRequest request) {
        return ApiResponse.success(messageService.loadMorePlatform(request));
    }

    @PostMapping("/api/v1/platform/messages/mark-read")
    public ApiResponse<MessageActionResult> markPlatformRead(@RequestBody MessageBulkActionRequest request) {
        return ApiResponse.success(messageService.markPlatformRead(request));
    }

    @PostMapping("/api/v1/platform/messages/archive")
    public ApiResponse<MessageActionResult> archivePlatform(@RequestBody MessageBulkActionRequest request) {
        return ApiResponse.success(messageService.archivePlatform(request));
    }

    @PostMapping("/api/v1/platform/messages/mark-all-read")
    public ApiResponse<MessageActionResult> markAllPlatformRead(@RequestBody MessageMarkAllReadRequest request) {
        return ApiResponse.success(messageService.markAllPlatformRead(request));
    }

    @PostMapping("/api/v1/systems/{systemId}/messages/search")
    public ApiResponse<PageResult<MessageCardVO>> systemMessages(@PathVariable String systemId,
                                                                 @RequestParam(defaultValue = "1") int pageNo,
                                                                 @RequestParam(defaultValue = "20") int pageSize,
                                                                 @RequestBody MessageQueryRequest query) {
        return ApiResponse.success(messageService.systemMessages(systemId, new PageRequest(pageNo, pageSize, null,
                List.of(), List.of()), query));
    }

    @PostMapping("/api/v1/systems/{systemId}/messages/load-more")
    public ApiResponse<MessageLoadMoreResult> loadMoreSystem(@PathVariable String systemId,
                                                             @RequestBody MessageLoadMoreRequest request) {
        return ApiResponse.success(messageService.loadMoreSystem(systemId, request));
    }

    @PostMapping("/api/v1/systems/{systemId}/messages/mark-read")
    public ApiResponse<MessageActionResult> markSystemRead(@PathVariable String systemId,
                                                           @RequestBody MessageBulkActionRequest request) {
        return ApiResponse.success(messageService.markSystemRead(systemId, request));
    }

    @PostMapping("/api/v1/systems/{systemId}/messages/archive")
    public ApiResponse<MessageActionResult> archiveSystem(@PathVariable String systemId,
                                                          @RequestBody MessageBulkActionRequest request) {
        return ApiResponse.success(messageService.archiveSystem(systemId, request));
    }

    @PostMapping("/api/v1/systems/{systemId}/messages/mark-all-read")
    public ApiResponse<MessageActionResult> markAllSystemRead(@PathVariable String systemId,
                                                              @RequestBody MessageMarkAllReadRequest request) {
        return ApiResponse.success(messageService.markAllSystemRead(systemId, request));
    }
}
