package com.ayshriv.authplatform.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation enforcing step-up verification / recent authentication on sensitive actions.
 * Endpoints marked with this annotation require that the caller's JWT token has an auth_time
 * claim within the allowed window (default configured via app.mfa.step-up-validity-minutes)
 * or has completed MFA verification.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresRecentMfa {
    /**
     * Optional override for max validity in minutes.
     * Default <= 0 means use application property configuration.
     */
    long maxAgeMinutes() default -1;
}
