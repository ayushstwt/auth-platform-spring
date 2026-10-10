package com.iosocket.userservice.exception;

import com.iosocket.userservice.constants.ErrorMessages;

public class BadRequestException extends BaseException {

    public BadRequestException(ErrorMessages errorMessage, Object... args) {
        super(errorMessage, args);
    }

    public BadRequestException(String message) {
        super(ErrorMessages.E005, message);
    }
}
