package com.iosocket.userservice.exception;

import com.iosocket.userservice.constants.ErrorMessages;

public class ResourceAlreadyExistException extends BaseException {

    public ResourceAlreadyExistException(ErrorMessages errorMessage, Object... args) {
        super(errorMessage, args);
    }

    public ResourceAlreadyExistException(String message) {
        super(ErrorMessages.E003, message);
    }
}
