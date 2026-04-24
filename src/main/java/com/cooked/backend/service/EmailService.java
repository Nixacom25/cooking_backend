package com.cooked.backend.service;

public interface EmailService {
    void sendOtpEmail(String to, String otp);
    void sendAccountUpdateEmail(String to, String message);
}
