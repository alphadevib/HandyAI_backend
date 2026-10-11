package com.handyai.build.exception;

import com.handyai.build.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every failure into the same JSON envelope, so the frontend has exactly one error shape to
 * handle and stack traces never leak to the browser.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        String message = fieldErrors.values().stream().findFirst()
                .orElse("Please check the submitted values");
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.validation(message, request.getRequestURI(), fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpServletRequest request) {
        return status(HttpStatus.BAD_REQUEST, "Request body is missing or malformed", request);
    }

    /**
     * A URL nobody mapped, a method that endpoint does not accept, or a query parameter that is
     * missing or the wrong type. Without these the catch-all below would report every one of them
     * as a 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(HttpServletRequest request) {
        return status(HttpStatus.NOT_FOUND, "No endpoint matches this URL", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaType(HttpServletRequest request) {
        return status(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Send this request as application/json", request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotAcceptable(HttpServletRequest request) {
        return status(HttpStatus.NOT_ACCEPTABLE, "This endpoint only returns JSON", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return status(HttpStatus.METHOD_NOT_ALLOWED,
                ex.getMethod() + " is not supported by this endpoint", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return status(HttpStatus.BAD_REQUEST,
                "Missing required parameter: " + ex.getParameterName(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return status(HttpStatus.BAD_REQUEST,
                "Parameter '" + ex.getName() + "' has an unexpected value", request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleParameterValidation(HttpServletRequest request) {
        return status(HttpStatus.BAD_REQUEST, "Please check the submitted values", request);
    }

    /** Anything that already carries a status keeps it instead of being flattened to a 500. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleStatusException(ResponseStatusException ex,
                                                                  HttpServletRequest request) {
        HttpStatus resolved = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus httpStatus = resolved == null ? HttpStatus.INTERNAL_SERVER_ERROR : resolved;
        return status(httpStatus, ex.getReason() == null ? httpStatus.getReasonPhrase()
                : ex.getReason(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException ex,
                                                             HttpServletRequest request) {
        if (ex.getFieldErrors() != null) {
            return ResponseEntity.badRequest().body(ApiErrorResponse.validation(ex.getMessage(),
                    request.getRequestURI(), ex.getFieldErrors()));
        }
        return status(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(UnauthorizedException ex,
                                                               HttpServletRequest request) {
        return status(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiErrorResponse> handleTooMany(TooManyRequestsException ex,
                                                          HttpServletRequest request) {
        return status(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(ForbiddenException ex,
                                                            HttpServletRequest request) {
        return status(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                           HttpServletRequest request) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex,
                                                           HttpServletRequest request) {
        return status(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * A unique-constraint race (two parallel registrations for one email, or a double-tapped
     * favourite) surfaces here as a clean 409 rather than a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleIntegrity(DataIntegrityViolationException ex,
                                                            HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMessage());
        return status(HttpStatus.CONFLICT,
                "That record already exists. Please refresh and try again.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex,
                                                             HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return status(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our side. Please try again.", request);
    }

    private ResponseEntity<ApiErrorResponse> status(HttpStatus status, String message,
                                                    HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status.value(),
                status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
