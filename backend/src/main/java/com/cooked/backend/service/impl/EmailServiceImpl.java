package com.cooked.backend.service.impl;

import com.cooked.backend.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
 
    @org.springframework.beans.factory.annotation.Value("${spring.mail.from}")
    private String senderEmail;

    private static final String OTP_TEMPLATE = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; background-color: #F9FAFB; color: #1F2937; margin: 0; padding: 0; }
                .container { max-width: 600px; margin: 40px auto; background: #FFFFFF; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); border: 1px solid #E5E7EB; }
                .header { background-color: #C83A2D; padding: 30px; text-align: center; }
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
                .header { background-color: #C83A2D; padding: 30px; text-align: center; }
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

    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("Cooked <" + senderEmail + ">");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send HTML email to {} from {}: {}", to, senderEmail, e.getMessage(), e);
        }
    }
}
