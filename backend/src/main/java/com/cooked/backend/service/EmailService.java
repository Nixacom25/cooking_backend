package com.cooked.backend.service;

public interface EmailService {
    void sendOtpEmail(String to, String otp);
    void sendAccountUpdateEmail(String to, String message);
    void sendWelcomeEmail(String to, String firstName);
    void sendAccountDeletedEmail(String to, String firstName);
    void sendNewDeviceSignInEmail(String to, String firstName, String device, String location, String dateTime);
    void sendSupportRequestReceivedEmail(String to, String firstName, String ticketNumber, String subject);
    void sendSupportNotificationToTeam(String teamEmail, String ticketNumber, String subject, String fromName, String fromEmail, String message);
    void sendPaymentFailureEmail(String to, String firstName, String planName, String price);
    void sendTrialEndsTomorrowEmail(String to, String firstName, String planName, String price);
}
