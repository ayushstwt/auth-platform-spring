package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.constants.ErrorMessages;
import com.ayshriv.authplatform.exception.UnauthorizedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OAuthExchangeCodeService {

    private final AppProperties appProperties;
    private final Map<String, ExchangeCodeEntry> codeStore = new ConcurrentHashMap<>();

    public OAuthExchangeCodeService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public String generateCode(Long userId) {
        String code = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(appProperties.google().exchangeCodeTtlSeconds());
        codeStore.put(code, new ExchangeCodeEntry(userId, expiresAt));
        return code;
    }

    public Long consumeCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new UnauthorizedException(ErrorMessages.E027);
        }
        ExchangeCodeEntry entry = codeStore.remove(code.trim());
        if (entry == null || entry.isExpired()) {
            throw new UnauthorizedException(ErrorMessages.E027);
        }
        return entry.userId();
    }

    private record ExchangeCodeEntry(Long userId, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
