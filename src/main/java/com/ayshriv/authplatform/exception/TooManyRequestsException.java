package com.ayshriv.authplatform.exception;

import com.ayshriv.authplatform.constants.ErrorMessages;

public class TooManyRequestsException extends BaseException {

    public TooManyRequestsException() {
        super(ErrorMessages.E016);
    }

    public TooManyRequestsException(String message) {
        super(ErrorMessages.E016, message);
    }
}
