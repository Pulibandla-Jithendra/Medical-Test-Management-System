package com.medicore.exception;

import org.springframework.http.HttpStatus;

public class DomainException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    public static DomainException notFound(String message) {
        return new DomainException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
    public static DomainException forbidden(String message) {
        return new DomainException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
    public static DomainException conflict(String message) {
        return new DomainException(HttpStatus.CONFLICT, "CONFLICT", message);
    }
    public static DomainException unauthorized() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.");
    }
    public static DomainException invalid(String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }
}
