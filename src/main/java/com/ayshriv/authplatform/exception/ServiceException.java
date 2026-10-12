package com.ayshriv.authplatform.exception;

import com.ayshriv.authplatform.constants.ErrorMessages;

public class ServiceException extends BaseException {

    public ServiceException(ErrorMessages errorMessage, Object... args) {
        super(errorMessage, args);
    }

    public ServiceException(String message) {
        super(ErrorMessages.E500, message);
    }

    public ServiceException(ErrorMessages errorMessage, Throwable cause, Object... args) {
        super(errorMessage, cause, args);
    }
}
