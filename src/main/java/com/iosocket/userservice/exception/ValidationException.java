package com.iosocket.userservice.exception;

import com.iosocket.userservice.constants.ErrorMessages;

public class ValidationException extends BaseException {

    public ValidationException(ErrorMessages errorMessage, Object... args) {
        super(errorMessage, args);
    }

    public ValidationException(String message) {
        super(ErrorMessages.E005, message);
    }
}
