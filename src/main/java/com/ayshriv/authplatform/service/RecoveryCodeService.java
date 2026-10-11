package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.entity.UserRecoveryCode;
import com.ayshriv.authplatform.repository.UserRecoveryCodeRepository;
import com.ayshriv.authplatform.util.OtpCryptoUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service managing backup single-use recovery codes for MFA.
 * Generates 10 human-friendly codes (e.g., 4-4 alphanumeric formatted as XXXX-XXXX),
 * hashes them using server pepper before saving to database, and validates consumption.
 */
@Service
public class RecoveryCodeService {

    private static final int CODE_COUNT = 10;
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // Base32 without easily confused 0/O, 1/I
    private static final int CODE_LENGTH = 8;

    private final UserRecoveryCodeRepository recoveryCodeRepository;
    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public RecoveryCodeService(UserRecoveryCodeRepository recoveryCodeRepository,
                               AppProperties appProperties) {
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.appProperties = appProperties;
    }

    /**
     * Generates a fresh set of 10 recovery codes for the given user, deletes any existing codes,
     * stores the hashed codes, and returns the plain codes (displayed to the user once).
     *
     * @param user user entity
     * @return list of 10 plain-text recovery codes formatted as XXXX-XXXX
     */
    @Transactional
    public List<String> generateAndSaveRecoveryCodes(User user) {
        recoveryCodeRepository.deleteAllByUserId(user.getId());

        List<String> plainCodes = new ArrayList<>(CODE_COUNT);
        List<UserRecoveryCode> entities = new ArrayList<>(CODE_COUNT);

        for (int i = 0; i < CODE_COUNT; i++) {
            String raw = generateSingleCode();
            String formatted = raw.substring(0, 4) + "-" + raw.substring(4, 8);
            plainCodes.add(formatted);

            String hash = hashRecoveryCode(user.getId(), formatted);
            entities.add(new UserRecoveryCode(user, hash));
        }

        recoveryCodeRepository.saveAll(entities);
        return plainCodes;
    }

    /**
     * Validates and consumes a recovery code for the given user.
     *
     * @param user user entity
     * @param submittedCode plain recovery code submitted by user
     * @return true if valid and successfully consumed, false otherwise
     */
    @Transactional
    public boolean validateAndConsumeRecoveryCode(User user, String submittedCode) {
        if (submittedCode == null || submittedCode.isBlank()) {
            return false;
        }

        String normalized = normalizeCode(submittedCode);
        String hash = hashRecoveryCode(user.getId(), normalized);

        return recoveryCodeRepository.findByUserIdAndCodeHash(user.getId(), hash)
                .filter(rc -> !rc.isConsumed())
                .map(rc -> {
                    rc.setConsumedAt(Instant.now());
                    recoveryCodeRepository.save(rc);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Counts the remaining unconsumed recovery codes for the user.
     */
    @Transactional(readOnly = true)
    public long countRemainingCodes(Long userId) {
        return recoveryCodeRepository.countByUserIdAndConsumedAtIsNull(userId);
    }

    /**
     * Clears all recovery codes for the user (e.g. when MFA is disabled).
     */
    @Transactional
    public void deleteAllForUser(Long userId) {
        recoveryCodeRepository.deleteAllByUserId(userId);
    }

    private String generateSingleCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private String normalizeCode(String code) {
        String clean = code.trim().toUpperCase().replace("-", "").replace(" ", "");
        if (clean.length() == 8) {
            return clean.substring(0, 4) + "-" + clean.substring(4, 8);
        }
        return code.trim().toUpperCase();
    }

    private String hashRecoveryCode(Long userId, String code) {
        String pepper = appProperties.otp().pepper();
        return OtpCryptoUtils.hashOtp(code, "recovery:" + userId, pepper);
    }
}
