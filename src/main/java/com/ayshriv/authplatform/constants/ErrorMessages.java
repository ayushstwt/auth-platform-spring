package com.ayshriv.authplatform.constants;

import org.springframework.http.HttpStatus;

public enum ErrorMessages {
    E001(HttpStatus.NOT_FOUND, "Resource not found"),
    E002(HttpStatus.NOT_FOUND, "User with identifier %s not found"),
    E003(HttpStatus.CONFLICT, "Resource already exists"),
    E004(HttpStatus.UNAUTHORIZED, "Authentication required"),
    E005(HttpStatus.BAD_REQUEST, "Validation failed"),
    E006(HttpStatus.FORBIDDEN, "Access denied"),
    E007(HttpStatus.BAD_REQUEST, "Malformed or unreadable request payload"),
    E008(HttpStatus.METHOD_NOT_ALLOWED, "Request method '%s' not supported for this endpoint"),
    E009(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Media type '%s' is not supported"),
    E010(HttpStatus.BAD_REQUEST, "Required request parameter '%s' is missing"),
    E011(HttpStatus.BAD_REQUEST, "Invalid value '%s' for parameter '%s'"),
    E012(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    E013(HttpStatus.UNAUTHORIZED, "Token expired or invalid"),
    E014(HttpStatus.FORBIDDEN, "Email address has not been verified"),
    E015(HttpStatus.BAD_REQUEST, "Verification code is invalid or has expired"),
    E016(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please wait before trying again"),
    E017(HttpStatus.BAD_REQUEST, "Maximum attempts reached. Please request a new code"),
    E018(HttpStatus.BAD_REQUEST, "Invalid phone number format. Must be a valid E.164 phone number"),
    E019(HttpStatus.FORBIDDEN, "SMS delivery to this country or destination is not permitted"),
    E020(HttpStatus.UNAUTHORIZED, "Recent re-authentication is required to change a verified phone number"),
    E021(HttpStatus.TOO_MANY_REQUESTS, "Daily SMS quota exceeded. Please contact support"),
    E022(HttpStatus.UNAUTHORIZED, "Invalid, unverified, or expired Google token"),
    E023(HttpStatus.FORBIDDEN, "A local account exists with this email but is not verified. Please verify your email first"),
    E024(HttpStatus.BAD_REQUEST, "Cannot unlink the last remaining login method"),
    E025(HttpStatus.CONFLICT, "This Google account is already linked to another user"),
    E026(HttpStatus.FORBIDDEN, "Hosted domain unauthorized"),
    E027(HttpStatus.UNAUTHORIZED, "OAuth exchange code is invalid, already used, or expired"),
    E028(HttpStatus.UNAUTHORIZED, "MFA token is invalid, expired, or maximum attempts reached"),
    E029(HttpStatus.UNAUTHORIZED, "Invalid MFA code or recovery code"),
    E030(HttpStatus.FORBIDDEN, "Requested MFA factor is not configured or verified for this account"),
    E031(HttpStatus.FORBIDDEN, "Step-up authentication required: recent MFA or login required for this operation"),
    E032(HttpStatus.BAD_REQUEST, "MFA is already enabled on this account"),
    E033(HttpStatus.BAD_REQUEST, "MFA is not enabled on this account"),
    E034(HttpStatus.FORBIDDEN, "MFA is required for administrator accounts"),
    E500(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal error occurred");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorMessages(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }

    public String format(Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        return String.format(message, args);
    }
}
