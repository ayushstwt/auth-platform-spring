package com.ayshriv.authplatform.exception;

import com.ayshriv.authplatform.constants.ErrorMessages;

public class AppException extends BaseException {

    public AppException(ErrorMessages errorMessage, Object... args) {
        super(errorMessage, args);
    }

    public AppException(String message) {
        super(ErrorMessages.E500, message);
    }

    public AppException(ErrorMessages errorMessage, Throwable cause, Object... args) {
        super(errorMessage, cause, args);
    }
}
