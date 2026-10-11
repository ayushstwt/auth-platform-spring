package com.ayshriv.authplatform.entity;

public enum LogAction {
    LOGIN,
    LOGOUT,
    REGISTER,
    PASSWORD_RESET,
    PASSWORD_CHANGE,
    EMAIL_VERIFY,
    PHONE_VERIFY,
    PHONE_ADD,
    PHONE_CHANGE,
    OTP_REQUEST,
    OTP_VERIFY,
    OAUTH_LOGIN,
    MFA_SETUP,
    MFA_VERIFY,
    ACCOUNT_UPDATE,
    ACCOUNT_LOCK,
    TOKEN_REFRESH,
    TOKEN_REUSE_DETECTED,
    SYSTEM
}
