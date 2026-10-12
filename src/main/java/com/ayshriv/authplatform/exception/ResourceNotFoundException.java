package com.ayshriv.authplatform.exception;

import com.ayshriv.authplatform.constants.ErrorMessages;

public class ResourceNotFoundException extends BaseException {

    public ResourceNotFoundException(ErrorMessages errorMessage, Object... args) {
        super(errorMessage, args);
    }

    public ResourceNotFoundException(String message) {
        super(ErrorMessages.E001, message);
    }
}
