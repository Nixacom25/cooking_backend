package com.cooked.backend.exception;

public class PaymentRequiredException extends RuntimeException {
    public PaymentRequiredException(String message) {
        super(message);
    }
}
