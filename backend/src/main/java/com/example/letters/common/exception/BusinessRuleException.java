package com.example.letters.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A business rule stopped the request, e.g. a run already generating for the letter type.
 * Rendered as a ProblemDetail with a stable {@code code} the frontend can act on. Not found is
 * {@code jakarta.persistence.EntityNotFoundException} instead, as in the rest of the codebase.
 */
public class BusinessRuleException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessRuleException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
