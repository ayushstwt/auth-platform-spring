package com.ayshriv.authplatform.service;

public interface GoogleTokenVerifier {

    GoogleIdTokenPayload verify(String idTokenString);
}
