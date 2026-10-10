package com.iosocket.userservice.util;

import java.util.regex.Pattern;

public final class LogMaskingUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?<=.)[^@](?=[^@]*?[^@]@)|(?<=@.)[^.](?=[^.]*?\\.)");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b(\\+?\\d{1,3}[- ]?)?(\\d{2,3})[- ]?(\\d{3,4})[- ]?(\\d{4})\\b");

    private LogMaskingUtils() {
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "[EMPTY]";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + (atIndex >= 0 ? email.substring(atIndex) : "");
        }
        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);
        if (localPart.length() <= 2) {
            return localPart.charAt(0) + "***" + domainPart;
        }
        return localPart.charAt(0) + "***" + localPart.charAt(localPart.length() - 1) + domainPart;
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "[EMPTY]";
        }
        String cleaned = phone.replaceAll("[^0-9+]", "");
        if (cleaned.length() <= 4) {
            return "****";
        }
        return "*******" + cleaned.substring(cleaned.length() - 4);
    }

    public static String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "[EMPTY]";
        }
        if (token.length() <= 8) {
            return "[REDACTED]";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
