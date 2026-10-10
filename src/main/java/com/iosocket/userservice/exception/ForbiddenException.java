package com.iosocket.userservice.exception;

import com.iosocket.userservice.constants.ErrorMessages;

public class ForbiddenException extends BaseException {

    public ForbiddenException(ErrorMessages errorMessage, Object... args) {
        super(errorMessage, args);
    }

    public ForbiddenException(String message) {
        super(ErrorMessages.E006, message);
    }
}
