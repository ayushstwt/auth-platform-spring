package com.iosocket.userservice.exception;

import com.iosocket.userservice.constants.ErrorMessages;

public abstract class BaseException extends RuntimeException {

    private final ErrorMessages errorMessage;

    protected BaseException(ErrorMessages errorMessage, Object... args) {
        super(computeMessage(errorMessage, args));
        this.errorMessage = errorMessage;
    }

    protected BaseException(String message, ErrorMessages errorMessage) {
        super(message != null ? message : errorMessage.getMessage());
        this.errorMessage = errorMessage;
    }

    protected BaseException(ErrorMessages errorMessage, Throwable cause, Object... args) {
        super(computeMessage(errorMessage, args), cause);
        this.errorMessage = errorMessage;
    }

    public ErrorMessages getErrorMessage() {
        return errorMessage;
    }

    private static String computeMessage(ErrorMessages em, Object... args) {
        if (args == null || args.length == 0) {
            return em.getMessage();
        }
        if (args.length == 1 && args[0] instanceof String customMsg && !em.getMessage().contains("%s")) {
            return customMsg;
        }
        return em.format(args);
    }
}
