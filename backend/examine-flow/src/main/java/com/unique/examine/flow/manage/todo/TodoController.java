package com.unique.examine.flow.manage.todo;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.flow.manage.todo.TodoModels.TodoActionRequest;
import com.unique.examine.flow.manage.todo.TodoModels.TodoActionResult;
import com.unique.examine.flow.manage.todo.TodoModels.TodoSearchRequest;
import com.unique.examine.flow.manage.todo.TodoModels.TodoSearchResult;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 待办工作台 API 控制器。
 */
@RestController
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    /**
     * 查询平台待办工作台。
     *
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @param request 待办筛选条件
     * @return 待办类型树和分页列表
     */
    @PostMapping("/api/v1/platform/todos/search")
    public ApiResponse<TodoSearchResult> searchPlatform(@RequestParam(defaultValue = "1") int pageNo,
                                                        @RequestParam(defaultValue = "20") int pageSize,
                                                        @RequestBody(required = false)
                                                        TodoSearchRequest request) {
        return ApiResponse.success(todoService.searchPlatform(new PageRequest(pageNo, pageSize,
                request == null ? null : request.keyword(), List.of(), List.of()), request));
    }

    /**
     * 查询当前系统待办工作台。
     *
     * @param systemId 系统编号
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @param request 待办筛选条件
     * @return 待办类型树和分页列表
     */
    @PostMapping("/api/v1/systems/{systemId}/todos/search")
    public ApiResponse<TodoSearchResult> searchSystem(@PathVariable String systemId,
                                                      @RequestParam(defaultValue = "1") int pageNo,
                                                      @RequestParam(defaultValue = "20") int pageSize,
                                                      @RequestBody(required = false)
                                                      TodoSearchRequest request) {
        return ApiResponse.success(todoService.searchSystem(systemId, new PageRequest(pageNo, pageSize,
                request == null ? null : request.keyword(), List.of(), List.of()), request));
    }

    /**
     * 执行系统待办行级动作。
     *
     * @param systemId 系统编号
     * @param todoId 待办编号
     * @param actionCode 动作编码
     * @param request 动作参数
     * @return 待办动作结果
     */
    @PostMapping("/api/v1/systems/{systemId}/todos/{todoId}/actions/{actionCode}")
    public ApiResponse<TodoActionResult> executeSystemAction(@PathVariable String systemId,
                                                             @PathVariable String todoId,
                                                             @PathVariable String actionCode,
                                                             @RequestBody(required = false)
                                                             TodoActionRequest request) {
        return ApiResponse.success(todoService.executeSystemAction(systemId, todoId, actionCode, request));
    }
}
