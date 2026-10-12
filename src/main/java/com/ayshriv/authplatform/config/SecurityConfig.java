package com.ayshriv.authplatform.config;

import com.ayshriv.authplatform.security.CorrelationIdFilter;
import com.ayshriv.authplatform.security.JwtAudienceValidator;
import com.ayshriv.authplatform.security.RestAccessDeniedHandler;
import com.ayshriv.authplatform.security.RestAuthenticationEntryPoint;
import com.ayshriv.authplatform.security.RsaKeyProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final AppProperties appProperties;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final CorrelationIdFilter correlationIdFilter;
    private final RsaKeyProvider rsaKeyProvider;
    private final com.ayshriv.authplatform.security.OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final com.ayshriv.authplatform.security.RateLimitingFilter rateLimitingFilter;
    private final com.ayshriv.authplatform.security.TokenDenylistService tokenDenylistService;

    public SecurityConfig(AppProperties appProperties,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          CorrelationIdFilter correlationIdFilter,
                          RsaKeyProvider rsaKeyProvider,
                          com.ayshriv.authplatform.security.OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
                          com.ayshriv.authplatform.security.RateLimitingFilter rateLimitingFilter,
                          com.ayshriv.authplatform.security.TokenDenylistService tokenDenylistService) {
        this.appProperties = appProperties;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.correlationIdFilter = correlationIdFilter;
        this.rsaKeyProvider = rsaKeyProvider;
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
        this.rateLimitingFilter = rateLimitingFilter;
        this.tokenDenylistService = tokenDenylistService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .contentTypeOptions(contentType -> {})
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'none'; frame-ancestors 'none'")
                        )
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                                .preload(true)
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/.well-known/jwks.json",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/login/email-otp/send",
                                "/api/v1/auth/login/email-otp/confirm",
                                "/api/v1/auth/login/phone-otp/send",
                                "/api/v1/auth/login/phone-otp/confirm",
                                "/api/v1/auth/google",
                                "/api/v1/auth/google/exchange",
                                "/api/v1/auth/password/forgot",
                                "/api/v1/auth/password/reset",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api/v1/health"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );

        if (appProperties.google().redirectFlowEnabled()) {
            http.oauth2Login(oauth2 -> oauth2
                    .successHandler(oAuth2LoginSuccessHandler)
            );
        }

        http.addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(correlationIdFilter, com.ayshriv.authplatform.security.RateLimitingFilter.class);

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withPublicKey(rsaKeyProvider.getPublicKey()).build();

        OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefaultWithIssuer(appProperties.security().jwt().issuer());
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtAudienceValidator(appProperties.security().jwt().audience());
        OAuth2TokenValidator<Jwt> revocationValidator = new com.ayshriv.authplatform.security.JwtRevocationValidator(tokenDenylistService);
        OAuth2TokenValidator<Jwt> combinedValidator = new DelegatingOAuth2TokenValidator<>(defaultValidator, audienceValidator, revocationValidator);

        jwtDecoder.setJwtValidator(combinedValidator);
        return jwtDecoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return authenticationConverter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        AppProperties.CorsProperties corsProps = appProperties.security().cors();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProps.allowedOrigins());
        config.setAllowedMethods(corsProps.allowedMethods());
        config.setAllowedHeaders(corsProps.allowedHeaders());
        config.setAllowCredentials(corsProps.allowCredentials());
        config.setMaxAge(corsProps.maxAgeSeconds());
        config.setExposedHeaders(List.of("X-Request-Id", "Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
