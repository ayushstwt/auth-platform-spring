package com.ayshriv.authplatform.exception;

import com.ayshriv.authplatform.constants.ErrorMessages;

public class ResourceAlreadyExistException extends BaseException {

    public ResourceAlreadyExistException(ErrorMessages errorMessage, Object... args) {
        super(errorMessage, args);
    }

    public ResourceAlreadyExistException(String message) {
        super(ErrorMessages.E003, message);
    }
}
