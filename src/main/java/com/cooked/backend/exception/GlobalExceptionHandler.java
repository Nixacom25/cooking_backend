package com.cooked.backend.exception;

import com.cooked.backend.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), "BACKEND", "RESOURCE_NOT_FOUND");
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(EmailAlreadyExistsException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), "BACKEND", "EMAIL_EXISTS");
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), "BACKEND", "BAD_REQUEST");
    }

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ErrorResponse> handleAiService(AiServiceException ex) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), "IA", ex.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation failed: " + errors.toString(), "VALIDATION", "INVALID_INPUT");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Access Denied", "AUTH", "FORBIDDEN");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication Failed: " + ex.getMessage(), "AUTH", "UNAUTHORIZED");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        // Log the exception for internal tracking
        ex.printStackTrace();
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred: " + ex.getMessage(), "BACKEND", "INTERNAL_SERVER_ERROR");
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String message, String source, String errorCode) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(status.value())
                .message(message)
                .source(source)
                .errorCode(errorCode)
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(errorResponse, status);
    }
}
