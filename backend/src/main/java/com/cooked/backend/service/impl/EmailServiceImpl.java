package com.cooked.backend.service.impl;

import com.cooked.backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class EmailServiceImpl implements EmailService {

    private final org.springframework.web.client.RestTemplate restTemplate;
 
    @org.springframework.beans.factory.annotation.Value("${spring.mail.from}")
    private String senderEmail;

    @org.springframework.beans.factory.annotation.Value("${spring.mail.password}")
    private String brevoApiKey;

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    // Templates (OTP_TEMPLATE, ACCOUNT_UPDATE_TEMPLATE) remain the same...

    private static final String OTP_TEMPLATE = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; background-color: #F9FAFB; color: #1F2937; margin: 0; padding: 0; }
                .container { max-width: 600px; margin: 40px auto; background: #FFFFFF; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); border: 1px solid #E5E7EB; }
                .header { background-color: #FFFFFF; padding: 30px; text-align: center; border-bottom: 1px solid #E5E7EB; }
                .content { padding: 40px; text-align: center; }
                .otp-code { font-size: 32px; font-weight: 800; color: #C83A2D; letter-spacing: 4px; background: #FEE2E2; padding: 15px 30px; border-radius: 12px; display: inline-block; margin: 20px 0; border: 1px solid #FECACA; }
                .footer { padding: 30px; text-align: center; font-size: 13px; color: #6B7280; background-color: #F9FAFB; border-top: 1px solid #F3F4F6; }
                h1 { font-size: 24px; font-weight: 700; margin-bottom: 20px; color: #111827; }
                p { line-height: 1.6; margin-bottom: 16px; font-size: 15px; }
                .team { font-weight: 700; color: #111827; margin-top: 25px; }
                .coordinates { color: #9CA3AF; margin-top: 5px; }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <!-- Hosted Logo -->
                    <img src="https://res.cloudinary.com/davj7mdjj/image/upload/v1776889984/ai-recipe-app/branding/rz2853c3krx8s55ary25.png" alt="Cooked" style="height: 45px;">
                </div>
                <div class="content">
                    <h1>Verify your account</h1>
                    <p>Hello,</p>
                    <p>To complete your registration and secure your Cooked account, please use the following verification code:</p>
                    <div class="otp-code">%s</div>
                    <p>Enter it to continue.</p>
                    <p>This code will expire soon. If you didn't request this code, please ignore this email.</p>
                    <p class="team">Cooked Team</p>
                </div>
                <div class="footer">
                    <p><strong>Cooked</strong></p>
                    <p class="coordinates">contact@cookedapp.com | +1 (234) 567-890</p>
                    <p>&copy; 2026 Cooked. All rights reserved.</p>
                </div>
            </div>
        </body>
        </html>
        """;

    private static final String ACCOUNT_UPDATE_TEMPLATE = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; background-color: #F9FAFB; color: #1F2937; margin: 0; padding: 0; }
                .container { max-width: 600px; margin: 40px auto; background: #FFFFFF; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); border: 1px solid #E5E7EB; }
                .header { background-color: #FFFFFF; padding: 30px; text-align: center; border-bottom: 1px solid #E5E7EB; }
                .content { padding: 40px; text-align: center; }
                .footer { padding: 30px; text-align: center; font-size: 13px; color: #6B7280; background-color: #F9FAFB; border-top: 1px solid #F3F4F6; }
                h1 { font-size: 24px; font-weight: 700; margin-bottom: 20px; color: #111827; }
                p { line-height: 1.6; margin-bottom: 16px; font-size: 15px; }
                .team { font-weight: 700; color: #111827; margin-top: 25px; }
                .coordinates { color: #9CA3AF; margin-top: 5px; }
                .warning { background-color: #FFFBEB; border-left: 4px solid #F59E0B; padding: 15px; margin-top: 25px; text-align: left; font-size: 14px; color: #92400E; }
            </style>
        </head>
        <body>
            <div class="container">
                
                <div class="content">
                    <h1>Account Updated</h1>
                    <p>Hello,</p>
                    <p>%s</p>
                    <p>If you made this change, you can safely ignore this email.</p>
                    <div class="warning">
                        <strong>Security Notice:</strong> If you did NOT perform this action, please contact our support team immediately to secure your account.
                    </div>
                    <p class="team">Cooked Team</p>
                </div>
                <div class="footer">
                    <img src="https://res.cloudinary.com/davj7mdjj/image/upload/v1776889984/ai-recipe-app/branding/rz2853c3krx8s55ary25.png" alt="Cooked" style="height: 45px;">
                    <p><strong>Team Cooked</strong></p>
                    <p class="coordinates">contact@cookedapp.com | +1 (234) 567-890</p>
                    <p>&copy; 2026 Cooked. All rights reserved.</p>
                </div>
            </div>
        </body>
        </html>
        """;

    private static final String LOGO_URL = "https://res.cloudinary.com/davj7mdjj/image/upload/v1776889984/ai-recipe-app/branding/rz2853c3krx8s55ary25.png";
    private static final String APP_HOME_URL = "https://cookedapp.com";
    private static final String MANAGE_SUBSCRIPTION_URL = "https://cookedapp.com/manage-subscription";

    private static final String BUTTON_STYLE = ".btn { display: inline-block; background-color: #C83A2D; color: #FFFFFF !important; text-decoration: none; font-weight: 700; font-size: 15px; padding: 14px 32px; border-radius: 999px; margin: 10px 0 20px; }";

    private static final String WELCOME_TEMPLATE = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; background-color: #F9FAFB; color: #1F2937; margin: 0; padding: 0; }
                .container { max-width: 600px; margin: 40px auto; background: #FFFFFF; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); border: 1px solid #E5E7EB; }
                .header { background-color: #FFFFFF; padding: 30px; text-align: center; border-bottom: 1px solid #E5E7EB; }
                .content { padding: 40px; text-align: center; }
                .footer { padding: 30px; text-align: center; font-size: 13px; color: #6B7280; background-color: #F9FAFB; border-top: 1px solid #F3F4F6; }
                h1 { font-size: 24px; font-weight: 700; margin-bottom: 20px; color: #111827; }
                p { line-height: 1.6; margin-bottom: 16px; font-size: 15px; }
                .team { font-weight: 700; color: #111827; margin-top: 25px; }
                .coordinates { color: #9CA3AF; margin-top: 5px; }
                %s
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header"><img src="%s" alt="Cooked" style="height: 45px;"></div>
                <div class="content">
                    <h1>Welcome to Cooked</h1>
                    <p>Hi %s,</p>
                    <p>Welcome to Cooked.</p>
                    <p>Cooked helps you turn the ingredients you already have into personalized recipes, discover new meals, save your favorites, and build smarter grocery lists.</p>
                    <p>A great place to start is with your first scan. Take a photo of what you have in your kitchen and we'll help you figure out what to cook.</p>
                    <a class="btn" href="%s">Open Cooked</a>
                    <p class="team">The Cooked Team</p>
                </div>
                <div class="footer">
                    <p><strong>Cooked</strong></p>
                    <p class="coordinates">contact@cookedapp.com</p>
                    <p>&copy; 2026 Cooked. All rights reserved.</p>
                </div>
            </div>
        </body>
        </html>
        """;

    private static final String ACCOUNT_DELETED_TEMPLATE = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; background-color: #F9FAFB; color: #1F2937; margin: 0; padding: 0; }
                .container { max-width: 600px; margin: 40px auto; background: #FFFFFF; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); border: 1px solid #E5E7EB; }
                .header { background-color: #FFFFFF; padding: 30px; text-align: center; border-bottom: 1px solid #E5E7EB; }
                .content { padding: 40px; text-align: center; }
                .footer { padding: 30px; text-align: center; font-size: 13px; color: #6B7280; background-color: #F9FAFB; border-top: 1px solid #F3F4F6; }
                h1 { font-size: 24px; font-weight: 700; margin-bottom: 20px; color: #111827; }
                p { line-height: 1.6; margin-bottom: 16px; font-size: 15px; }
                .team { font-weight: 700; color: #111827; margin-top: 25px; }
                .coordinates { color: #9CA3AF; margin-top: 5px; }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header"><img src="%s" alt="Cooked" style="height: 45px;"></div>
                <div class="content">
                    <h1>Your Cooked account has been deleted</h1>
                    <p>Hi %s,</p>
                    <p>Your Cooked account has been deleted.</p>
                    <p>Your information will be handled according to our Privacy Policy and applicable data-retention requirements.</p>
                    <p>If you'd like to use Cooked again in the future, you can create a new account.</p>
                    <p>Thank you for using Cooked.</p>
                    <p class="team">The Cooked Team</p>
                </div>
                <div class="footer">
                    <p><strong>Cooked</strong></p>
                    <p class="coordinates">contact@cookedapp.com</p>
                    <p>&copy; 2026 Cooked. All rights reserved.</p>
                </div>
            </div>
        </body>
        </html>
        """;

    private static final String NEW_DEVICE_TEMPLATE = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; background-color: #F9FAFB; color: #1F2937; margin: 0; padding: 0; }
                .container { max-width: 600px; margin: 40px auto; background: #FFFFFF; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); border: 1px solid #E5E7EB; }
                .header { background-color: #FFFFFF; padding: 30px; text-align: center; border-bottom: 1px solid #E5E7EB; }
                .content { padding: 40px; text-align: center; }
                .details { background: #F9FAFB; border-radius: 12px; padding: 18px 20px; margin: 20px 0; text-align: left; font-size: 14px; color: #374151; }
                .details p { margin: 4px 0; }
                .footer { padding: 30px; text-align: center; font-size: 13px; color: #6B7280; background-color: #F9FAFB; border-top: 1px solid #F3F4F6; }
                h1 { font-size: 24px; font-weight: 700; margin-bottom: 20px; color: #111827; }
                p { line-height: 1.6; margin-bottom: 16px; font-size: 15px; }
                .team { font-weight: 700; color: #111827; margin-top: 25px; }
                .coordinates { color: #9CA3AF; margin-top: 5px; }
                %s
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header"><img src="%s" alt="Cooked" style="height: 45px;"></div>
                <div class="content">
                    <h1>New sign-in to your Cooked account</h1>
                    <p>Hi %s,</p>
                    <p>We noticed a new sign-in to your Cooked account.</p>
                    <div class="details">
                        <p><strong>Device:</strong> %s</p>
                        <p><strong>Location:</strong> %s</p>
                        <p><strong>Time:</strong> %s</p>
                    </div>
                    <p>If this was you, no action is needed.</p>
                    <p>If you don't recognize this activity, secure your account immediately.</p>
                    <a class="btn" href="%s">Secure My Account</a>
                    <p class="team">The Cooked Team</p>
                </div>
                <div class="footer">
                    <p><strong>Cooked</strong></p>
                    <p class="coordinates">contact@cookedapp.com</p>
                    <p>&copy; 2026 Cooked. All rights reserved.</p>
                </div>
            </div>
        </body>
        </html>
        """;

    private static final String SUPPORT_RECEIVED_TEMPLATE = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; background-color: #F9FAFB; color: #1F2937; margin: 0; padding: 0; }
                .container { max-width: 600px; margin: 40px auto; background: #FFFFFF; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); border: 1px solid #E5E7EB; }
                .header { background-color: #FFFFFF; padding: 30px; text-align: center; border-bottom: 1px solid #E5E7EB; }
                .content { padding: 40px; text-align: center; }
                .details { background: #F9FAFB; border-radius: 12px; padding: 18px 20px; margin: 20px 0; text-align: left; font-size: 14px; color: #374151; }
                .details p { margin: 4px 0; }
                .footer { padding: 30px; text-align: center; font-size: 13px; color: #6B7280; background-color: #F9FAFB; border-top: 1px solid #F3F4F6; }
                h1 { font-size: 24px; font-weight: 700; margin-bottom: 20px; color: #111827; }
                p { line-height: 1.6; margin-bottom: 16px; font-size: 15px; }
                .team { font-weight: 700; color: #111827; margin-top: 25px; }
                .coordinates { color: #9CA3AF; margin-top: 5px; }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header"><img src="%s" alt="Cooked" style="height: 45px;"></div>
                <div class="content">
                    <h1>We received your Cooked support request</h1>
                    <p>Hi %s,</p>
                    <p>We've received your request.</p>
                    <div class="details">
                        <p><strong>Ticket:</strong> #%s</p>
                        <p><strong>Topic:</strong> %s</p>
                    </div>
                    <p>Our team will review it and respond as soon as possible.</p>
                    <p>You can reply directly to this email if you'd like to provide additional information.</p>
                    <p class="team">The Cooked Support Team</p>
                </div>
                <div class="footer">
                    <p><strong>Cooked</strong></p>
                    <p class="coordinates">contact@cookedapp.com</p>
                    <p>&copy; 2026 Cooked. All rights reserved.</p>
                </div>
            </div>
        </body>
        </html>
        """;

    private static final String PAYMENT_FAILURE_TEMPLATE = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; background-color: #F9FAFB; color: #1F2937; margin: 0; padding: 0; }
                .container { max-width: 600px; margin: 40px auto; background: #FFFFFF; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); border: 1px solid #E5E7EB; }
                .header { background-color: #FFFFFF; padding: 30px; text-align: center; border-bottom: 1px solid #E5E7EB; }
                .content { padding: 40px; text-align: center; }
                .details { background: #FFFBEB; border: 1px solid #FDE68A; border-radius: 12px; padding: 18px 20px; margin: 20px 0; text-align: left; font-size: 14px; color: #92400E; }
                .details p { margin: 4px 0; }
                .footer { padding: 30px; text-align: center; font-size: 13px; color: #6B7280; background-color: #F9FAFB; border-top: 1px solid #F3F4F6; }
                h1 { font-size: 24px; font-weight: 700; margin-bottom: 20px; color: #111827; }
                p { line-height: 1.6; margin-bottom: 16px; font-size: 15px; }
                .team { font-weight: 700; color: #111827; margin-top: 25px; }
                .coordinates { color: #9CA3AF; margin-top: 5px; }
                %s
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header"><img src="%s" alt="Cooked" style="height: 45px;"></div>
                <div class="content">
                    <h1>Action needed: Your Cooked payment didn't go through</h1>
                    <p>Hi %s,</p>
                    <p>We weren't able to process your latest Cooked subscription payment.</p>
                    <div class="details">
                        <p><strong>Plan:</strong> %s</p>
                        <p><strong>Amount:</strong> %s</p>
                    </div>
                    <p>Please update your payment information to avoid losing access to your Cooked subscription.</p>
                    <a class="btn" href="%s">Update Payment Method</a>
                    <p>If you've already resolved the issue, no further action is needed.</p>
                    <p class="team">The Cooked Team</p>
                </div>
                <div class="footer">
                    <p><strong>Cooked</strong></p>
                    <p class="coordinates">contact@cookedapp.com</p>
                    <p>&copy; 2026 Cooked. All rights reserved.</p>
                </div>
            </div>
        </body>
        </html>
        """;

    private static final String TRIAL_ENDS_TEMPLATE = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; background-color: #F9FAFB; color: #1F2937; margin: 0; padding: 0; }
                .container { max-width: 600px; margin: 40px auto; background: #FFFFFF; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); border: 1px solid #E5E7EB; }
                .header { background-color: #FFFFFF; padding: 30px; text-align: center; border-bottom: 1px solid #E5E7EB; }
                .content { padding: 40px; text-align: center; }
                .footer { padding: 30px; text-align: center; font-size: 13px; color: #6B7280; background-color: #F9FAFB; border-top: 1px solid #F3F4F6; }
                h1 { font-size: 24px; font-weight: 700; margin-bottom: 20px; color: #111827; }
                p { line-height: 1.6; margin-bottom: 16px; font-size: 15px; }
                .team { font-weight: 700; color: #111827; margin-top: 25px; }
                .coordinates { color: #9CA3AF; margin-top: 5px; }
                %s
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header"><img src="%s" alt="Cooked" style="height: 45px;"></div>
                <div class="content">
                    <h1>Your Cooked free trial ends tomorrow</h1>
                    <p>Hi %s,</p>
                    <p>Your Cooked free trial ends tomorrow.</p>
                    <p>After your trial ends, your %s subscription will automatically begin at %s.</p>
                    <p>If you'd like to continue using Cooked, there's nothing you need to do.</p>
                    <p>You can manage or cancel your subscription through your subscription settings.</p>
                    <a class="btn" href="%s">Manage Subscription</a>
                    <p class="team">The Cooked Team</p>
                </div>
                <div class="footer">
                    <p><strong>Cooked</strong></p>
                    <p class="coordinates">contact@cookedapp.com</p>
                    <p>&copy; 2026 Cooked. All rights reserved.</p>
                </div>
            </div>
        </body>
        </html>
        """;

    @Async
    @Override
    public void sendOtpEmail(String to, String otp) {
        sendHtmlEmail(to, "Verify your account", String.format(OTP_TEMPLATE, otp));
    }

    @Async
    @Override
    public void sendAccountUpdateEmail(String to, String message) {
        sendHtmlEmail(to, "Account Security Update", String.format(ACCOUNT_UPDATE_TEMPLATE, message));
    }

    @Async
    @Override
    public void sendWelcomeEmail(String to, String firstName) {
        String name = displayName(firstName);
        sendHtmlEmail(to, "Welcome to Cooked",
                String.format(WELCOME_TEMPLATE, BUTTON_STYLE, LOGO_URL, name, APP_HOME_URL));
    }

    @Async
    @Override
    public void sendAccountDeletedEmail(String to, String firstName) {
        String name = displayName(firstName);
        sendHtmlEmail(to, "Your Cooked account has been deleted",
                String.format(ACCOUNT_DELETED_TEMPLATE, LOGO_URL, name));
    }

    @Async
    @Override
    public void sendNewDeviceSignInEmail(String to, String firstName, String device, String location, String dateTime) {
        String name = displayName(firstName);
        sendHtmlEmail(to, "New sign-in to your Cooked account",
                String.format(NEW_DEVICE_TEMPLATE, BUTTON_STYLE, LOGO_URL, name,
                        device == null || device.isBlank() ? "Unknown device" : device,
                        location == null || location.isBlank() ? "Unknown location" : location,
                        dateTime, APP_HOME_URL));
    }

    private static final String SUPPORT_TEAM_NOTIFICATION_TEMPLATE = """
        <!DOCTYPE html>
        <html>
        <head><meta charset="UTF-8"></head>
        <body style="font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; color: #1F2937;">
            <h2>New support ticket #%s</h2>
            <p><strong>From:</strong> %s (%s)</p>
            <p><strong>Subject:</strong> %s</p>
            <hr>
            <p style="white-space: pre-wrap;">%s</p>
            <hr>
            <p>Reply to this email to answer the customer directly.</p>
        </body>
        </html>
        """;

    @Async
    @Override
    public void sendSupportNotificationToTeam(String teamEmail, String ticketNumber, String subject, String fromName, String fromEmail, String message) {
        sendHtmlEmail(teamEmail, "New support request: " + subject,
                String.format(SUPPORT_TEAM_NOTIFICATION_TEMPLATE, ticketNumber, fromName, fromEmail, subject, message),
                fromEmail);
    }

    @Async
    @Override
    public void sendSupportRequestReceivedEmail(String to, String firstName, String ticketNumber, String subject) {
        String name = displayName(firstName);
        sendHtmlEmail(to, "We received your Cooked support request",
                String.format(SUPPORT_RECEIVED_TEMPLATE, LOGO_URL, name, ticketNumber, subject));
    }

    @Async
    @Override
    public void sendPaymentFailureEmail(String to, String firstName, String planName, String price) {
        String name = displayName(firstName);
        sendHtmlEmail(to, "Action needed: Your Cooked payment didn't go through",
                String.format(PAYMENT_FAILURE_TEMPLATE, BUTTON_STYLE, LOGO_URL, name, planName, price, MANAGE_SUBSCRIPTION_URL));
    }

    @Async
    @Override
    public void sendTrialEndsTomorrowEmail(String to, String firstName, String planName, String price) {
        String name = displayName(firstName);
        sendHtmlEmail(to, "Your Cooked free trial ends tomorrow",
                String.format(TRIAL_ENDS_TEMPLATE, BUTTON_STYLE, LOGO_URL, name, planName, price, MANAGE_SUBSCRIPTION_URL));
    }

    private String displayName(String firstName) {
        return firstName == null || firstName.isBlank() ? "there" : firstName.trim();
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        sendHtmlEmail(to, subject, htmlContent, null);
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent, String replyToEmail) {
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("sender", java.util.Map.of("name", "Cooked", "email", senderEmail));
            body.put("to", java.util.List.of(java.util.Map.of("email", to)));
            body.put("subject", subject);
            body.put("htmlContent", htmlContent);
            if (replyToEmail != null && !replyToEmail.isBlank()) {
                body.put("replyTo", java.util.Map.of("email", replyToEmail));
            }

            org.springframework.http.HttpEntity<java.util.Map<String, Object>> entity = 
                new org.springframework.http.HttpEntity<>(body, headers);

            org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(
                BREVO_API_URL, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Email sent successfully to {} via Brevo API", to);
            } else {
                log.error("Failed to send email to {} via Brevo API. Status: {}, Body: {}", 
                    to, response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to send HTML email to {} from {} via Brevo API: {}", 
                to, senderEmail, e.getMessage(), e);
        }
    }
}
