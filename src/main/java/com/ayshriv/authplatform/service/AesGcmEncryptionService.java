package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * Service providing AES-256-GCM encryption and decryption for sensitive secrets like TOTP keys.
 * Supports key rotation via key identifiers stored in the encrypted payload format:
 * v1:{keyId}:{iv_base64}:{ciphertext_base64}
 */
@Service
public class AesGcmEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final String FORMAT_PREFIX = "v1";

    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmEncryptionService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Encrypts plaintext using AES-256-GCM and the current active encryption key ID.
     *
     * @param plaintext sensitive plain text to encrypt
     * @return formatted encrypted string v1:{keyId}:{iv_base64}:{ciphertext_base64}
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }

        String currentKeyId = appProperties.mfa().currentKeyId();
        String hexOrRawKey = getKeyById(currentKeyId);

        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        try {
            SecretKey secretKey = buildSecretKey(hexOrRawKey);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String cipherTextBase64 = Base64.getEncoder().encodeToString(cipherText);

            return String.format("%s:%s:%s:%s", FORMAT_PREFIX, currentKeyId, ivBase64, cipherTextBase64);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt data with AES-GCM", e);
        }
    }

    /**
     * Decrypts ciphertext formatted as v1:{keyId}:{iv_base64}:{ciphertext_base64}.
     *
     * @param encryptedData formatted encrypted string
     * @return decrypted plaintext
     */
    public String decrypt(String encryptedData) {
        if (encryptedData == null) {
            return null;
        }

        String[] parts = encryptedData.split(":");
        if (parts.length != 4 || !FORMAT_PREFIX.equals(parts[0])) {
            throw new IllegalArgumentException("Invalid encrypted payload format");
        }

        String keyId = parts[1];
        String ivBase64 = parts[2];
        String cipherTextBase64 = parts[3];

        String hexOrRawKey = getKeyById(keyId);
        byte[] iv = Base64.getDecoder().decode(ivBase64);
        byte[] cipherText = Base64.getDecoder().decode(cipherTextBase64);

        try {
            SecretKey secretKey = buildSecretKey(hexOrRawKey);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt AES-GCM ciphertext", e);
        }
    }

    public String getCurrentKeyId() {
        return appProperties.mfa().currentKeyId();
    }

    private String getKeyById(String keyId) {
        Map<String, String> keys = appProperties.mfa().encryptionKeys();
        if (keys == null || !keys.containsKey(keyId)) {
            throw new IllegalStateException("Encryption key not found for keyId: " + keyId);
        }
        return keys.get(keyId);
    }

    private SecretKey buildSecretKey(String keyString) {
        byte[] keyBytes;
        // 1. Support Hex string (64 characters)
        if (keyString.length() == 64 && keyString.matches("^[0-9a-fA-F]+$")) {
            keyBytes = hexStringToByteArray(keyString);
        } else {
            // 2. Try Base64 decoding if applicable
            try {
                byte[] decoded = Base64.getDecoder().decode(keyString);
                if (decoded.length == 32) {
                    return new SecretKeySpec(decoded, "AES");
                }
            } catch (IllegalArgumentException ignored) {
                // Not valid base64
            }

            // 3. Fallback to raw UTF-8 bytes
            byte[] rawBytes = keyString.getBytes(StandardCharsets.UTF_8);
            if (rawBytes.length != 32) {
                throw new IllegalArgumentException("AES-256 key must be exactly 32 bytes (or 64 hex characters / 44 base64 characters), got " + rawBytes.length);
            }
            keyBytes = rawBytes;
        }
        return new SecretKeySpec(keyBytes, "AES");
    }


    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
