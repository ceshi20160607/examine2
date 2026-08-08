package com.unique.examine.openapi.api;

import com.unique.examine.core.ai.PlatformOperationsQueryFacade;import com.unique.examine.core.api.*;import com.unique.examine.core.context.RequestSession;import com.unique.examine.core.error.BusinessException;import com.unique.examine.openapi.security.PlatformOpenApiMachineSession;
import jakarta.servlet.http.HttpServletRequest;import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;

@RestController
public class PlatformOpenApiTaskController {
    private final PlatformOperationsQueryFacade operations;public PlatformOpenApiTaskController(PlatformOperationsQueryFacade operations){this.operations=operations;}
    @GetMapping("/openapi/v1/platform/tasks") public ApiResponse<PlatformOperationsQueryFacade.PersonalTasksResult> tasks(@RequestParam(defaultValue="20")int limit,@RequestAttribute(value=RequestSession.REQUEST_ATTRIBUTE,required=false)Object value,HttpServletRequest request){if(!(value instanceof PlatformOpenApiMachineSession session))throw new BusinessException("OPENAPI_AUTH_REQUIRED","Platform OpenAPI authentication is required",HttpStatus.UNAUTHORIZED);var result=operations.query(new PlatformOperationsQueryFacade.Request(session.accountId(),session.permissionVersion(),PlatformOperationsQueryFacade.QueryKind.PERSONAL_TASKS,limit));if(!(result instanceof PlatformOperationsQueryFacade.PersonalTasksResult tasks))throw new IllegalStateException("Unexpected platform operation result");return ApiResponse.success(tasks,attr(request,WebRequestAttributes.REQUEST_ID),attr(request,WebRequestAttributes.TRACE_ID));}
    private static String attr(HttpServletRequest r,String n){var v=r.getAttribute(n);return v==null?"":String.valueOf(v);}
}
