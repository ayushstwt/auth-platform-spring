package com.ayshriv.authplatform.exception;

import com.ayshriv.authplatform.constants.ErrorMessages;

public class ValidationException extends BaseException {

    public ValidationException(ErrorMessages errorMessage, Object... args) {
        super(errorMessage, args);
    }

    public ValidationException(String message) {
        super(ErrorMessages.E005, message);
    }
}
