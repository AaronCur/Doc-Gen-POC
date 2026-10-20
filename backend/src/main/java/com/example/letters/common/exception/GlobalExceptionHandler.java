package com.example.letters.common.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.persistence.EntityNotFoundException;

/**
 * Every error is an RFC 9457 ProblemDetail with a stable {@code code} property:
 * {@code NOT_FOUND}, {@code VALIDATION_FAILED}, {@code BAD_REQUEST}, a business-rule code such as
 * {@code RUN_IN_PROGRESS}, and so on. 401 and 403 come from Spring Security before a controller is
 * reached, so they are not ProblemDetails in the POC.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public static final String CODE = "code";

    // 1. Not found (unknown run, letter type or document)
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleEntityNotFound(EntityNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    // 2. Business rules (409 RUN_IN_PROGRESS, 422 NO_ELIGIBLE_APPLICATIONS, ...)
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleException ex) {
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    // 3. Bean validation on request bodies
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return validationFailed(errors);
    }

    // 4. Bean validation through method validation. A controller with constraints on its parameters
    //    (page and size) has its @Valid body validated this way too, so a request body's
    //    errors arrive as ParameterErrors and are reported per field, like handler 3.
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException ex) {
        List<String> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result instanceof ParameterErrors bodyErrors
                        ? bodyErrors.getFieldErrors().stream()
                                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        : result.getResolvableErrors().stream()
                                .map(error -> result.getMethodParameter().getParameterName() + ": "
                                        + error.getDefaultMessage()))
                .toList();
        return validationFailed(errors);
    }

    // 5. Malformed JSON, or a value of the wrong type (e.g. an unparseable date)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Malformed JSON request body or invalid data types.");
    }

    // 6. Missing required request header
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException ex) {
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                String.format("Required header '%s' is missing", ex.getHeaderName()));
    }

    // 7. Missing query parameter
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParams(MissingServletRequestParameterException ex) {
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                String.format("Required query parameter '%s' is missing", ex.getParameterName()));
    }

    // 8. Unsupported HTTP method
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                String.format("HTTP method '%s' is not supported for this endpoint. Supported methods: %s",
                        ex.getMethod(), ex.getSupportedHttpMethods()));
    }

    // 9. Unsupported content type
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", ex.getMessage());
    }

    // 10. Unknown path (including the OpenAPI paths when springdoc is switched off)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "No endpoint at " + ex.getResourcePath());
    }

    // 11. Framework errors that carry their own status, e.g. an unsupported Api-Version header
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String code = status != null ? status.name() : String.valueOf(ex.getStatusCode().value());
        return problem(ex.getStatusCode(), code, ex.getReason());
    }

    // 12. A path or query value of the wrong type (e.g. /api/letter-runs/abc)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String type = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "the expected type";
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                String.format("Parameter '%s' must be of type %s", ex.getName(), type));
    }

    // 13. Database constraint violations
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        return problem(HttpStatus.CONFLICT, "CONFLICT",
                "Database conflict: the record may already exist or violates a database constraint.");
    }

    // 14. Anything else: 500 without leaking details. This handler runs before Spring's own resolver,
    //     so any other framework exception that carries a status (406, 413, ...) keeps that status here.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGlobalException(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse && !errorResponse.getStatusCode().is5xxServerError()) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            String code = status != null ? status.name() : String.valueOf(errorResponse.getStatusCode().value());
            return problem(errorResponse.getStatusCode(), code, errorResponse.getBody().getDetail());
        }
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred on the server.");
    }

    private static ResponseEntity<ProblemDetail> validationFailed(List<String> errors) {
        ResponseEntity<ProblemDetail> response =
                problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.");
        response.getBody().setProperty("errors", errors);
        return response;
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE, code);
        return ResponseEntity.status(status).body(problem);
    }
}
