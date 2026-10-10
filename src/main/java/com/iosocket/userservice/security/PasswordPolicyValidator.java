package com.iosocket.userservice.security;

import com.iosocket.userservice.constants.ErrorMessages;
import com.iosocket.userservice.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class PasswordPolicyValidator {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password123456",
            "123456789012",
            "qwerty123456",
            "admin12345678",
            "welcome123456",
            "letmein123456",
            "iloveyou12345",
            "sunshine12345",
            "monkey1234567",
            "football12345",
            "passw0rd12345",
            "master1234567",
            "dragon1234567",
            "trustno112345",
            "changeme12345"
    );

    public void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new ValidationException(ErrorMessages.E005, "Password must be at least " + MIN_LENGTH + " characters long");
        }
        if (password.length() > MAX_LENGTH) {
            throw new ValidationException(ErrorMessages.E005, "Password cannot exceed " + MAX_LENGTH + " characters");
        }
        if (COMMON_PASSWORDS.contains(password.toLowerCase())) {
            throw new ValidationException(ErrorMessages.E005, "Password is too common or easily guessable");
        }
    }
}
