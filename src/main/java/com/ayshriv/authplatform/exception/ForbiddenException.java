package com.ayshriv.authplatform.exception;

import com.ayshriv.authplatform.constants.ErrorMessages;

public class ForbiddenException extends BaseException {

    public ForbiddenException(ErrorMessages errorMessage, Object... args) {
        super(errorMessage, args);
    }

    public ForbiddenException(String message) {
        super(ErrorMessages.E006, message);
    }
}
