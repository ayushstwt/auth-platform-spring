package com.iosocket.userservice.exception;

import com.iosocket.userservice.constants.ErrorMessages;

public class UnauthorizedException extends BaseException {

    public UnauthorizedException(ErrorMessages errorMessage, Object... args) {
        super(errorMessage, args);
    }

    public UnauthorizedException(String message) {
        super(ErrorMessages.E004, message);
    }
}
