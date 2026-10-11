package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private TotpService totpService;

    // RFC 6238 Appendix B test vector seed: "12345678901234567890" (ASCII, 20 bytes)
    // Base32 representation of ASCII "12345678901234567890" is "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    private static final String RFC_6238_BASE32_SECRET = new Base32().encodeToString(
            "12345678901234567890".getBytes(StandardCharsets.US_ASCII)
    ).replace("=", "");

    @BeforeEach
    void setUp() {
        AppProperties.MfaProperties mfaProps = new AppProperties.MfaProperties(
                "k1",
                Map.of("k1", "01234567890123456789012345678901"),
                5,
                5,
                15,
                false,
                "AuthPlatform"
        );
        this.totpService = new TotpService(new AppProperties(null, null, null, null, null, mfaProps));
    }

    @Test
    @DisplayName("Verify RFC 6238 Test Vectors for SHA1 with 8-byte time steps")
    void testRfc6238Vectors() {
        // Table 1 from RFC 6238 (SHA1, 30s step, 8 digits modulo 6 digits test):
        // Time = 59s -> step = 1 -> "287082"
        assertThat(totpService.generateCode(RFC_6238_BASE32_SECRET, 59L)).isEqualTo("287082");

        // Time = 1111111109s -> step = 37037036 -> "081804"
        assertThat(totpService.generateCode(RFC_6238_BASE32_SECRET, 1111111109L)).isEqualTo("081804");

        // Time = 1111111111s -> step = 37037037 -> "050471"
        assertThat(totpService.generateCode(RFC_6238_BASE32_SECRET, 1111111111L)).isEqualTo("050471");

        // Time = 1234567890s -> step = 41152263 -> "005924"
        assertThat(totpService.generateCode(RFC_6238_BASE32_SECRET, 1234567890L)).isEqualTo("005924");

        // Time = 2000000000s -> step = 66666666 -> "279037"
        assertThat(totpService.generateCode(RFC_6238_BASE32_SECRET, 2000000000L)).isEqualTo("279037");
    }

    @Test
    @DisplayName("Verify valid code within clock skew window (+/- 1 step)")
    void verifyCodeWithinClockSkewWindow() {
        String secret = totpService.generateSecret();
        long currentStep = java.time.Instant.now().getEpochSecond() / 30;

        // Current step code
        String currentCode = totpService.generateCodeForTimeStep(secret, currentStep);
        Optional<Long> matchCurrent = totpService.verifyCode(secret, currentCode, null);
        assertThat(matchCurrent).isPresent().contains(currentStep);

        // Previous step (-1) code
        String prevCode = totpService.generateCodeForTimeStep(secret, currentStep - 1);
        Optional<Long> matchPrev = totpService.verifyCode(secret, prevCode, null);
        assertThat(matchPrev).isPresent().contains(currentStep - 1);

        // Next step (+1) code
        String nextCode = totpService.generateCodeForTimeStep(secret, currentStep + 1);
        Optional<Long> matchNext = totpService.verifyCode(secret, nextCode, null);
        assertThat(matchNext).isPresent().contains(currentStep + 1);

        // Outside window (-2 steps) must fail
        String oldCode = totpService.generateCodeForTimeStep(secret, currentStep - 2);
        Optional<Long> matchOld = totpService.verifyCode(secret, oldCode, null);
        assertThat(matchOld).isEmpty();
    }

    @Test
    @DisplayName("Prevent replay: reject code if timeStep is equal to or less than lastUsedTimeStep")
    void preventReplayAttack() {
        String secret = totpService.generateSecret();
        long currentStep = java.time.Instant.now().getEpochSecond() / 30;
        String currentCode = totpService.generateCodeForTimeStep(secret, currentStep);

        // First verification succeeds
        Optional<Long> firstVerify = totpService.verifyCode(secret, currentCode, null);
        assertThat(firstVerify).isPresent().contains(currentStep);

        // Replay of the same code with lastUsedTimeStep recorded as currentStep is rejected
        Optional<Long> replayVerify = totpService.verifyCode(secret, currentCode, currentStep);
        assertThat(replayVerify).isEmpty();

        // Even with next timestep, an older step is rejected
        Optional<Long> oldStepReplay = totpService.verifyCode(secret, totpService.generateCodeForTimeStep(secret, currentStep - 1), currentStep);
        assertThat(oldStepReplay).isEmpty();
    }
}
