package authorization.jwt.core;

import shared.core.AuthorizationServiceException;

public class JwtVerificationException extends AuthorizationServiceException {

    public JwtVerificationException(String message) {
        super(message);
    }

    public JwtVerificationException(String message, Throwable cause) {
        super(message, cause);
    }

}