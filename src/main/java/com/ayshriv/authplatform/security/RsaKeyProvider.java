package com.ayshriv.authplatform.security;

import com.ayshriv.authplatform.config.AppProperties;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);

    private final AppProperties appProperties;
    private final Map<String, RSAKey> keyStore = new ConcurrentHashMap<>();
    private volatile String activeKeyId;

    public RsaKeyProvider(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void init() {
        rotateKey();
    }

    public synchronized String rotateKey() {
        String newKeyId = "auth-platform-key-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();

            RSAPublicKey pub = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey priv = (RSAPrivateKey) keyPair.getPrivate();

            RSAKey rsaJwk = new RSAKey.Builder(pub)
                    .privateKey(priv)
                    .keyID(newKeyId)
                    .build();

            keyStore.put(newKeyId, rsaJwk);
            this.activeKeyId = newKeyId;

            log.info("Rotated/initialized active RSA signing key with key ID: {}. Total keys in JWKS: {}", newKeyId, keyStore.size());
            return newKeyId;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to initialize RSA key pair", e);
        }
    }

    public RSAPublicKey getPublicKey() {
        try {
            return keyStore.get(activeKeyId).toRSAPublicKey();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException("Failed to extract active RSA public key", e);
        }
    }

    public RSAPrivateKey getPrivateKey() {
        try {
            return keyStore.get(activeKeyId).toRSAPrivateKey();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException("Failed to extract active RSA private key", e);
        }
    }

    public String getKeyId() {
        return activeKeyId;
    }

    public RSAKey getRsaJwk() {
        return keyStore.get(activeKeyId);
    }

    public RSAKey getKeyById(String kid) {
        return keyStore.get(kid);
    }

    public JWKSet getJwkSet() {
        List<JWK> publicKeys = new ArrayList<>();
        for (RSAKey key : keyStore.values()) {
            publicKeys.add(key.toPublicJWK());
        }
        return new JWKSet(publicKeys);
    }

    public Map<String, RSAKey> getAllKeys() {
        return Collections.unmodifiableMap(keyStore);
    }
}
