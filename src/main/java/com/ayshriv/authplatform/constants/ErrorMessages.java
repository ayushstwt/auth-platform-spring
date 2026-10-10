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
