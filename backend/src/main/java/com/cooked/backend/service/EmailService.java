package com.cooked.backend.service;

public interface EmailService {
    void sendOtpEmail(String to, String otp);
    void sendAccountUpdateEmail(String to, String message);
    void sendWelcomeEmail(String to, String firstName);
    void sendAccountDeletedEmail(String to, String firstName);
    void sendNewDeviceSignInEmail(String to, String firstName, String device, String location, String dateTime);
    void sendSupportRequestReceivedEmail(String to, String firstName, String ticketNumber, String subject);
    void sendSupportNotificationToTeam(String teamEmail, String ticketNumber, String subject, String fromName, String fromEmail, String message, String userId, String platform, String appVersion, String category);
    void sendPaymentFailureEmail(String to, String firstName, String planName, String price);
    void sendGiftCodeEmail(String to, String firstName, String planLabel, String code, String redeemUrl);
    /** Website gift: receipt to the buyer, with the code and who it's for. */
    void sendGiftPurchaseReceiptEmail(String to, String planLabel, String code, String recipientEmail, String redeemUrl);
    /** Website gift: the gift itself, sent to the friend. */
    void sendGiftReceivedEmail(String to, String senderName, String planLabel, String code, String redeemUrl);
    void sendTrialEndsTomorrowEmail(String to, String firstName, String planName, String price);
    /** Key figures for admins; rows are {label, value}. */
    void sendAdminSummaryEmail(String to, String subject, String title, java.util.List<String[]> rows);

    void sendCriticalErrorAlert(String teamEmail, String errorId, String errorType, String errorMessage, String userId, String userEmail, String platform, String osVersion, String appVersion, String context);
}
