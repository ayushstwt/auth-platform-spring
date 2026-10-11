package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmEncryptionServiceTest {

    private AesGcmEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        AppProperties.MfaProperties mfaProps = new AppProperties.MfaProperties(
                "key-1",
                Map.of(
                        "key-1", "01234567890123456789012345678901",
                        "key-2", "abcdefabcdefabcdefabcdefabcdefab"
                ),
                5,
                5,
                15,
                false,
                "AuthPlatformTest"
        );

        AppProperties appProperties = new AppProperties(
                null, null, null, null, null, mfaProps
        );
        this.encryptionService = new AesGcmEncryptionService(appProperties);
    }

    @Test
    @DisplayName("Should successfully encrypt and decrypt plaintext using AES-256-GCM")
    void encryptAndDecryptRoundTrip() {
        String plainSecret = "JBSWY3DPEHPK3PXP";
        String encrypted = encryptionService.encrypt(plainSecret);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted).startsWith("v1:key-1:");

        String decrypted = encryptionService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(plainSecret);
    }

    @Test
    @DisplayName("Should support decryption of ciphertexts encrypted under previous rotated keyId")
    void decryptWithRotatedKey() {
        // Construct service with key-2 as active, but decrypting a payload produced with key-1
        String plainSecret = "MYSECRETROTATION123";
        String encryptedKey1 = encryptionService.encrypt(plainSecret);
        assertThat(encryptedKey1).startsWith("v1:key-1:");

        // Switch active key to key-2
        AppProperties.MfaProperties rotatedProps = new AppProperties.MfaProperties(
                "key-2",
                Map.of(
                        "key-1", "01234567890123456789012345678901",
                        "key-2", "abcdefabcdefabcdefabcdefabcdefab"
                ),
                5,
                5,
                15,
                false,
                "AuthPlatformTest"
        );
        AesGcmEncryptionService rotatedService = new AesGcmEncryptionService(
                new AppProperties(null, null, null, null, null, rotatedProps)
        );

        // Can still decrypt key-1 payload
        String decrypted = rotatedService.decrypt(encryptedKey1);
        assertThat(decrypted).isEqualTo(plainSecret);

        // New encryption uses key-2
        String encryptedKey2 = rotatedService.encrypt(plainSecret);
        assertThat(encryptedKey2).startsWith("v1:key-2:");
        assertThat(rotatedService.decrypt(encryptedKey2)).isEqualTo(plainSecret);
    }

    @Test
    @DisplayName("Should fail when decrypting with missing key ID")
    void failWhenKeyNotFound() {
        String fakeEncrypted = "v1:unknown-key:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        assertThatThrownBy(() -> encryptionService.decrypt(fakeEncrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Encryption key not found");
    }
}
