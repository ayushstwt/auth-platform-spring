package com.ayshriv.authplatform.util;

import com.ayshriv.authplatform.constants.ErrorMessages;
import com.ayshriv.authplatform.exception.BadRequestException;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

public final class PhoneNumberValidationUtils {

    private static final PhoneNumberUtil PHONE_UTIL = PhoneNumberUtil.getInstance();

    private PhoneNumberValidationUtils() {
    }

    public static String normalizeToE164(String rawPhoneNumber, String defaultRegion) {
        if (rawPhoneNumber == null || rawPhoneNumber.isBlank()) {
            throw new BadRequestException(ErrorMessages.E018);
        }

        try {
            PhoneNumber number = PHONE_UTIL.parse(rawPhoneNumber.trim(), defaultRegion != null ? defaultRegion : "IN");
            if (!PHONE_UTIL.isValidNumber(number)) {
                throw new BadRequestException(ErrorMessages.E018);
            }
            return PHONE_UTIL.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            throw new BadRequestException(ErrorMessages.E018);
        }
    }

    public static String getRegionCodeForNumber(String e164Number) {
        if (e164Number == null || e164Number.isBlank()) {
            return null;
        }
        try {
            String sanitized = e164Number.trim().startsWith("+") ? e164Number.trim() : "+" + e164Number.trim();
            PhoneNumber number = PHONE_UTIL.parse(sanitized, "");
            return PHONE_UTIL.getRegionCodeForCountryCode(number.getCountryCode());
        } catch (NumberParseException e) {
            return null;
        }
    }
}
