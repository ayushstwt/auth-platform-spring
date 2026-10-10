package com.iosocket.userservice.exception;

import com.iosocket.userservice.constants.ErrorMessages;

public abstract class BaseException extends RuntimeException {

    private final ErrorMessages errorMessage;

    protected BaseException(ErrorMessages errorMessage, Object... args) {
        super(args == null || args.length == 0 ? errorMessage.getMessage() : errorMessage.format(args));
        this.errorMessage = errorMessage;
    }

    protected BaseException(ErrorMessages errorMessage, Throwable cause, Object... args) {
        super(args == null || args.length == 0 ? errorMessage.getMessage() : errorMessage.format(args), cause);
        this.errorMessage = errorMessage;
    }

    public ErrorMessages getErrorMessage() {
        return errorMessage;
    }
}
