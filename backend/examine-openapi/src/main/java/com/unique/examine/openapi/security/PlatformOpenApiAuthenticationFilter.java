package com.unique.examine.openapi.security;

import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.openapi.domain.OpenApiCallLog;
import com.unique.examine.openapi.service.OpenApiCallLogService;
import com.unique.examine.openapi.service.PlatformOpenApiCallLogService;
import jakarta.servlet.*;import jakarta.servlet.http.*;
import org.slf4j.*;import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import java.io.IOException;import java.util.*;

@Component @Order(Ordered.HIGHEST_PRECEDENCE+15)
public class PlatformOpenApiAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger LOG=LoggerFactory.getLogger(PlatformOpenApiAuthenticationFilter.class);
    private final PlatformOpenApiAuthenticator authenticator;private final PlatformOpenApiCallLogService logs;private final HandlerExceptionResolver exceptions;
    public PlatformOpenApiAuthenticationFilter(PlatformOpenApiAuthenticator authenticator,PlatformOpenApiCallLogService logs,@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptions){this.authenticator=authenticator;this.logs=logs;this.exceptions=exceptions;}
    protected boolean shouldNotFilter(HttpServletRequest r){return !r.getRequestURI().startsWith("/openapi/v1/platform/");}
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        var started=System.nanoTime();var attempt=new OpenApiAttempt();var key=request.getHeader("X-App-Key");var ip=request.getRemoteAddr()==null?"0.0.0.0":request.getRemoteAddr();var route=PlatformOpenApiRoutePolicy.resolve(request.getMethod(),request.getRequestURI());var template=route.map(PlatformOpenApiRoutePolicy::routeTemplate).orElse(request.getRequestURI());var category=OpenApiCallLog.ResultCategory.FAILED;var status=500;
        try{if(route.isEmpty())throw new BusinessException("OPENAPI_ROUTE_NOT_FOUND","Platform OpenAPI route was not found",org.springframework.http.HttpStatus.NOT_FOUND);var buffered=new BufferedOpenApiRequest(request);var headers=OpenApiHeaders.require(buffered);var auth=authenticator.authenticate(new PlatformOpenApiAuthenticator.Request(buffered.getMethod(),buffered.getRequestURI(),buffered.getQueryString(),buffered.bodyBytes(),ip,headers,route.get()),attempt);buffered.setAttribute(RequestSession.REQUEST_ATTRIBUTE,auth.session());buffered.setAttribute(PlatformOpenApiAuthentication.REQUEST_ATTRIBUTE,auth);chain.doFilter(buffered,response);status=response.getStatus();category=status<400?OpenApiCallLog.ResultCategory.SUCCESS:OpenApiCallLog.ResultCategory.FAILED;}
        catch(BusinessException e){status=e.status().value();category=category(e.code());if("OPENAPI_RATE_LIMITED".equals(e.code())&&e.data() instanceof Map<?,?> d){response.setHeader("Retry-After",String.valueOf(d.get("retryAfterSeconds")));response.setHeader("X-RateLimit-Limit",String.valueOf(d.get("limit")));response.setHeader("X-RateLimit-Remaining","0");}exceptions.resolveException(request,response,null,e);}
        finally{try{logs.record(new OpenApiCallLogService.Attempt(attempt.applicationId(),key,attempt.credentialVersion(),template,request.getMethod(),category,status,Math.max(0,(System.nanoTime()-started)/1_000_000),attr(request,WebRequestAttributes.REQUEST_ID),attr(request,WebRequestAttributes.TRACE_ID),ip));}catch(RuntimeException e){LOG.error("Failed to persist platform OpenAPI call log",e);}}
    }
    private static String attr(HttpServletRequest r,String n){var v=r.getAttribute(n);return v==null?UUID.randomUUID().toString():String.valueOf(v);}
    private static OpenApiCallLog.ResultCategory category(String c){return switch(c){case "OPENAPI_SIGNATURE_INVALID","OPENAPI_TIMESTAMP_INVALID","OPENAPI_CREDENTIAL_UNAVAILABLE"->OpenApiCallLog.ResultCategory.SIGNATURE_REJECTED;case "OPENAPI_REPLAY_DETECTED"->OpenApiCallLog.ResultCategory.REPLAY_REJECTED;case "OPENAPI_SCOPE_DENIED"->OpenApiCallLog.ResultCategory.SCOPE_REJECTED;case "OPENAPI_PERMISSION_DENIED"->OpenApiCallLog.ResultCategory.PERMISSION_REJECTED;case "OPENAPI_IP_DENIED"->OpenApiCallLog.ResultCategory.IP_REJECTED;case "OPENAPI_RATE_LIMITED"->OpenApiCallLog.ResultCategory.RATE_REJECTED;default->OpenApiCallLog.ResultCategory.AUTH_REJECTED;};}
}
