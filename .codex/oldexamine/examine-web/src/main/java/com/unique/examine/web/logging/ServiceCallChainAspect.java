package com.unique.examine.web.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ServiceCallChainAspect {

    @Around("execution(public * com.unique.examine..service..*(..))")
    public Object aroundService(ProceedingJoinPoint pjp) throws Throwable {
        String sig = pjp.getSignature().getDeclaringType().getSimpleName() + "#" + pjp.getSignature().getName();
        CallChainHolder.push(sig);
        return pjp.proceed();
    }
}

