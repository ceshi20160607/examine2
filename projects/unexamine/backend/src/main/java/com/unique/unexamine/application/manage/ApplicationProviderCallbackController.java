package com.unique.unexamine.application.manage;

import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 外部平台公开回调入口；连接身份由不可猜测连接键和各平台官方校验共同确认。 */
@RestController
@RequestMapping("/api/provider-callbacks/v1")
public class ApplicationProviderCallbackController {
    private final ApplicationProviderService service;

    public ApplicationProviderCallbackController(ApplicationProviderService service) {
        this.service = service;
    }

    @GetMapping(value = "/wecom/{connectionKey}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String verify(
            @PathVariable String connectionKey,
            @RequestParam("msg_signature") String signature,
            @RequestParam String timestamp,
            @RequestParam String nonce,
            @RequestParam String echostr,
            HttpServletRequest request) {
        return service.verifyWeComUrl(connectionKey, signature, timestamp, nonce, echostr,
                TraceIdFilter.current(request));
    }

    @PostMapping(value = "/wecom/{connectionKey}", consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE},
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String callback(
            @PathVariable String connectionKey,
            @RequestParam("msg_signature") String signature,
            @RequestParam String timestamp,
            @RequestParam String nonce,
            @RequestBody String body,
            HttpServletRequest request) {
        return service.handleWeComEvent(connectionKey, signature, timestamp, nonce, body,
                request.getRemoteAddr(), TraceIdFilter.current(request));
    }

    @PostMapping(value = "/feishu/{connectionKey}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> feishuCallback(
            @PathVariable String connectionKey,
            @RequestHeader(value = "X-Lark-Request-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Lark-Request-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Lark-Signature", required = false) String signature,
            @RequestBody String body,
            HttpServletRequest request) {
        return service.handleFeishuEvent(connectionKey, timestamp, nonce, signature, body,
                request.getRemoteAddr(), TraceIdFilter.current(request));
    }

    @PostMapping(value = "/dingtalk/{connectionKey}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> dingTalkCallback(
            @PathVariable String connectionKey,
            @RequestParam(value = "msg_signature", required = false) String messageSignature,
            @RequestParam(value = "signature", required = false) String signature,
            @RequestParam(value = "timestamp", required = false) String timestamp,
            @RequestParam(value = "timeStamp", required = false) String timeStamp,
            @RequestParam String nonce,
            @RequestBody String body,
            HttpServletRequest request) {
        String actualSignature = messageSignature == null || messageSignature.isBlank() ? signature : messageSignature;
        String actualTimestamp = timestamp == null || timestamp.isBlank() ? timeStamp : timestamp;
        return service.handleDingTalkEvent(connectionKey, actualTimestamp, nonce, actualSignature, body,
                request.getRemoteAddr(), TraceIdFilter.current(request));
    }
}
