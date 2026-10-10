package com.iosocket.userservice.exception;

import com.iosocket.userservice.constants.ErrorMessages;

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
