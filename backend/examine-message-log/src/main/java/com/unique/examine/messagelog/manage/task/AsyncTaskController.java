package com.unique.examine.messagelog.manage.task;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.messagelog.manage.task.AsyncTaskModels.AsyncTaskActionResult;
import com.unique.examine.messagelog.manage.task.AsyncTaskModels.AsyncTaskQueryRequest;
import com.unique.examine.messagelog.manage.task.AsyncTaskModels.AsyncTaskVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Async task API controller.
 */
@RestController
public class AsyncTaskController {

    private final AsyncTaskManageService taskService;

    public AsyncTaskController(AsyncTaskManageService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/api/v1/tasks/search")
    public ApiResponse<PageResult<AsyncTaskVO>> search(@RequestParam(defaultValue = "1") int pageNo,
                                                       @RequestParam(defaultValue = "20") int pageSize,
                                                       @RequestBody AsyncTaskQueryRequest query) {
        return ApiResponse.success(taskService.search(new PageRequest(pageNo, pageSize, null,
                List.of(), List.of()), query));
    }

    @GetMapping("/api/v1/tasks/{taskId}")
    public ApiResponse<AsyncTaskVO> detail(@PathVariable String taskId) {
        return ApiResponse.success(taskService.detail(taskId));
    }

    @PostMapping("/api/v1/tasks/{taskId}/cancel")
    public ApiResponse<AsyncTaskActionResult> cancel(@PathVariable String taskId) {
        return ApiResponse.success(taskService.cancel(taskId));
    }

    @PostMapping("/api/v1/tasks/{taskId}/retry")
    public ApiResponse<AsyncTaskActionResult> retry(@PathVariable String taskId) {
        return ApiResponse.success(taskService.retry(taskId));
    }
}
