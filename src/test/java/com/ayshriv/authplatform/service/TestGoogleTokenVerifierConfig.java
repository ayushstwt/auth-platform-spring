package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

@Configuration
@Profile("test")
public class TestGoogleTokenVerifierConfig {

    private final RSAKey testRsaKey;

    public TestGoogleTokenVerifierConfig() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            this.testRsaKey = new RSAKey.Builder((RSAPublicKey) kp.getPublic())
                    .privateKey((RSAPrivateKey) kp.getPrivate())
                    .keyID("test-google-key-" + UUID.randomUUID())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate test RSA key for GoogleTokenVerifier", e);
        }
    }

    public RSAKey getTestRsaKey() {
        return testRsaKey;
    }

    @Bean
    @Primary
    public GoogleTokenVerifier testGoogleTokenVerifier(AppProperties appProperties) {
        JWKSet jwkSet = new JWKSet(testRsaKey.toPublicJWK());
        ImmutableJWKSet<?> jwkSource = new ImmutableJWKSet<>(jwkSet);
        return new NimbusGoogleTokenVerifier(appProperties, (com.nimbusds.jose.jwk.source.JWKSource) jwkSource);
    }
}
