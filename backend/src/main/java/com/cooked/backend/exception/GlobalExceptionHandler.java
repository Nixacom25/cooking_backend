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

    @ExceptionHandler(PaymentRequiredException.class)
    public ResponseEntity<ErrorResponse> handlePaymentRequired(PaymentRequiredException ex) {
        return buildErrorResponse(HttpStatus.PAYMENT_REQUIRED, ex.getMessage(), "BACKEND", "PAYMENT_REQUIRED");
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
        System.err.println("Validation Error Details: " + errors.toString());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Please ensure all required fields are filled correctly.", "VALIDATION", "INVALID_INPUT");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Access Denied", "AUTH", "FORBIDDEN");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication Failed: " + ex.getMessage(), "AUTH", "UNAUTHORIZED");
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        String msg = "A database error occurred. Please try again.";
        String errorCode = "DATABASE_ERROR";
        
        if (ex.getMessage() != null) {
            String lowerMsg = ex.getMessage().toLowerCase();
            if (lowerMsg.contains("duplicate key")) {
                errorCode = "DUPLICATE_ENTRY";
                if (lowerMsg.contains("cookbooks") && lowerMsg.contains("name")) {
                    msg = "A cookbook with this name already exists.";
                } else if (lowerMsg.contains("phone") || lowerMsg.contains("téléphone")) {
                    msg = "This phone number is already associated with an account.";
                } else if (lowerMsg.contains("email") || lowerMsg.contains("users")) {
                    msg = "This account already exists. Please log in.";
                } else if (lowerMsg.contains("recipe_ingredients") || lowerMsg.contains("cookbook_recipes")) {
                    msg = "This recipe is already in this cookbook.";
                } else {
                    msg = "An item with this name already exists.";
                }
            } else if (lowerMsg.contains("value too long") || lowerMsg.contains("too long")) {
                msg = "One of the fields is too long. Please shorten your input.";
                errorCode = "DATA_TOO_LONG";
            }
        }
        
        return buildErrorResponse(HttpStatus.CONFLICT, msg, "DATABASE", errorCode);
    }

    @ExceptionHandler(org.springframework.transaction.TransactionSystemException.class)
    public ResponseEntity<ErrorResponse> handleTransaction(org.springframework.transaction.TransactionSystemException ex) {
        System.err.println("Transaction Error Details: " + ex.getMessage());
        if (ex.getRootCause() instanceof jakarta.validation.ConstraintViolationException) {
             System.err.println("Constraint Violation inside Transaction: " + ex.getRootCause().getMessage());
             return buildErrorResponse(HttpStatus.BAD_REQUEST, "Some of the provided information is invalid or missing.", "VALIDATION", "CONSTRAINT_VIOLATION");
        }
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "A transaction error occurred. Please try again.", "BACKEND", "TRANSACTION_ERROR");
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(jakarta.validation.ConstraintViolationException ex) {
        System.err.println("Constraint Violation Error Details: " + ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Some of the provided information is invalid or missing.", "VALIDATION", "CONSTRAINT_VIOLATION");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        // Log the exception for internal tracking
        System.err.println("CRITICAL_ERROR: " + ex.getMessage());
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Server error", "BACKEND", "INTERNAL_SERVER_ERROR");
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
