package com.iosocket.userservice.exception;

import com.iosocket.userservice.constants.ErrorMessages;

public class ResourceNotFoundException extends BaseException {

    public ResourceNotFoundException(ErrorMessages errorMessage, Object... args) {
        super(errorMessage, args);
    }

    public ResourceNotFoundException(String message) {
        super(ErrorMessages.E001, message);
    }
}
