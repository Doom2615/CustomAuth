// dev/doom/customauth/exceptions/AuthException.java
package dev.doom.customauth.exceptions;

public class AuthException extends Exception {
    public AuthException(String message) {
        super(message);
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
