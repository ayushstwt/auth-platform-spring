package com.ayshriv.authplatform.security;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.entity.Role;
import com.ayshriv.authplatform.entity.User;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final RsaKeyProvider rsaKeyProvider;
    private final AppProperties appProperties;

    public JwtTokenService(RsaKeyProvider rsaKeyProvider, AppProperties appProperties) {
        this.rsaKeyProvider = rsaKeyProvider;
        this.appProperties = appProperties;
    }

    public String generateAccessToken(User user) {
        return generateAccessToken(user, List.of("pwd"), Instant.now());
    }

    public String generateAccessToken(User user, List<String> amr, Instant authTime) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(appProperties.security().jwt().accessTokenExpiryMinutes()));

        List<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .toList();

        List<String> effectiveAmr = (amr != null && !amr.isEmpty()) ? amr : List.of("pwd");
        Instant effectiveAuthTime = authTime != null ? authTime : now;

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getId().toString())
                .issuer(appProperties.security().jwt().issuer())
                .audience(appProperties.security().jwt().audience())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiry))
                .claim("email", user.getEmail())
                .claim("roles", roleNames)
                .claim("amr", effectiveAmr)
                .claim("auth_time", effectiveAuthTime.getEpochSecond())
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaKeyProvider.getKeyId())
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claimsSet);

        try {
            RSASSASigner signer = new RSASSASigner(rsaKeyProvider.getPrivateKey());
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT access token", e);
        }
    }


    public long getAccessTokenExpirySeconds() {
        return appProperties.security().jwt().accessTokenExpiryMinutes() * 60;
    }
}
