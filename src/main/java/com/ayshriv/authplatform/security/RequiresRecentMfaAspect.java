package com.ayshriv.authplatform.security;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.constants.ErrorMessages;
import com.ayshriv.authplatform.exception.ForbiddenException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

/**
 * Aspect enforcing step-up authentication checks for methods annotated with @RequiresRecentMfa.
 */
@Aspect
@Component
public class RequiresRecentMfaAspect {

    private static final Logger log = LoggerFactory.getLogger(RequiresRecentMfaAspect.class);

    private final AppProperties appProperties;

    public RequiresRecentMfaAspect(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Around("@annotation(com.ayshriv.authplatform.security.RequiresRecentMfa) || @within(com.ayshriv.authplatform.security.RequiresRecentMfa)")
    public Object enforceRecentMfa(ProceedingJoinPoint joinPoint) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            // Not a JWT authenticated request; allow security filter chain to handle or reject
            return joinPoint.proceed();
        }

        RequiresRecentMfa annotation = getAnnotation(joinPoint);
        long maxAgeMinutes = annotation != null && annotation.maxAgeMinutes() > 0
                ? annotation.maxAgeMinutes()
                : appProperties.mfa().stepUpValidityMinutes();

        Long authTimeEpoch = jwt.getClaim("auth_time");
        if (authTimeEpoch == null) {
            // Fall back to issued-at time if auth_time is missing
            Instant iat = jwt.getIssuedAt();
            authTimeEpoch = iat != null ? iat.getEpochSecond() : 0L;
        }

        long elapsedMinutes = (Instant.now().getEpochSecond() - authTimeEpoch) / 60;

        List<String> amr = jwt.getClaimAsStringList("amr");
        boolean hasMfaAmr = amr != null && amr.stream().anyMatch(factor -> factor.startsWith("mfa"));

        // If authentication happened longer ago than maxAgeMinutes, step-up is strictly required
        if (elapsedMinutes > maxAgeMinutes) {
            log.warn("Step-up auth required for subject {}: auth was {} mins ago (limit is {} mins)",
                    jwt.getSubject(), elapsedMinutes, maxAgeMinutes);
            throw new ForbiddenException(ErrorMessages.E031);
        }

        return joinPoint.proceed();
    }

    private RequiresRecentMfa getAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequiresRecentMfa annotation = method.getAnnotation(RequiresRecentMfa.class);
        if (annotation == null) {
            annotation = joinPoint.getTarget().getClass().getAnnotation(RequiresRecentMfa.class);
        }
        return annotation;
    }
}
