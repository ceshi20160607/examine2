package com.unique.examine.event.platform;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/platform/messages")
public class PlatformMessageController {
    private final PlatformMessageService messages;
    public PlatformMessageController(PlatformMessageService messages){this.messages=messages;}
    @GetMapping
    public ApiResponse<PlatformMessageApiModels.Page> list(@RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="ALL") String type,@RequestParam(required=false) String templateCode,@RequestParam(required=false) String keyword,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestAttribute(value=RequestSession.REQUEST_ATTRIBUTE,required=false) Object value,HttpServletRequest request){return success(messages.list(session(value),status,type,templateCode,keyword,from,to,page,size),request);}
    @GetMapping("/unread-count") public ApiResponse<PlatformMessageApiModels.UnreadCount> unread(@RequestAttribute(value=RequestSession.REQUEST_ATTRIBUTE,required=false) Object value,HttpServletRequest request){return success(messages.unread(session(value)),request);}
    @PostMapping("/{messageId}:read") public ApiResponse<PlatformMessageApiModels.MessageView> read(@PathVariable String messageId,@RequestAttribute(value=RequestSession.REQUEST_ATTRIBUTE,required=false) Object value,HttpServletRequest request){return success(messages.read(session(value),messageId),request);}
    @PostMapping("/read-all") public ApiResponse<PlatformMessageApiModels.ChangedCount> readAll(@RequestAttribute(value=RequestSession.REQUEST_ATTRIBUTE,required=false) Object value,HttpServletRequest request){return success(messages.readAll(session(value)),request);}
    @PostMapping("/{messageId}:archive") public ApiResponse<PlatformMessageApiModels.MessageView> archive(@PathVariable String messageId,@RequestAttribute(value=RequestSession.REQUEST_ATTRIBUTE,required=false) Object value,HttpServletRequest request){return success(messages.archive(session(value),messageId),request);}
    private static RequestSession session(Object value){return value instanceof RequestSession session?session:null;}
    private static <T> ApiResponse<T> success(T data,HttpServletRequest request){return ApiResponse.success(data,attribute(request,WebRequestAttributes.REQUEST_ID),attribute(request,WebRequestAttributes.TRACE_ID));}
    private static String attribute(HttpServletRequest request,String name){var value=request.getAttribute(name);return value==null?"":String.valueOf(value);}
}
