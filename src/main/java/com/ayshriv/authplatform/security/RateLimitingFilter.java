package com.ayshriv.authplatform.security;

import com.ayshriv.authplatform.constants.ErrorMessages;
import com.ayshriv.authplatform.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(RateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Skip static docs or health endpoints
        if (path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.startsWith("/.well-known")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        String groupKey = resolveGroupKey(path, clientIp);

        RateLimitResult result = rateLimiter.tryConsumeWithResult(groupKey, 1);

        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.tokensRemaining()));

        if (!result.allowed()) {
            log.warn("Rate limit exceeded for key {} on path {}", groupKey, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            ApiResponse<Void> errorResponse = ApiResponse.error(
                    ErrorMessages.E016.name(),
                    ErrorMessages.E016.getMessage(),
                    path
            );
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveGroupKey(String path, String clientIp) {
        if (path.contains("/auth/login/phone-otp") || path.contains("/auth/login/email-otp") || path.contains("/users/me/phone/send-otp")) {
            return "rate:otp:" + clientIp;
        } else if (path.startsWith("/api/v1/auth")) {
            return "rate:auth:" + clientIp;
        } else if (path.startsWith("/api/v1/admin")) {
            return "rate:admin:" + clientIp;
        } else {
            return "rate:default:" + clientIp;
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }
}
