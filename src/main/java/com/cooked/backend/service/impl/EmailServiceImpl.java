package com.cooked.backend.service.impl;

import com.cooked.backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Override
    public void sendOtpEmail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("Cooked <no-reply@cooked.app>");
        message.setTo(to);
        message.setSubject("Votre code de vérification Cooked");
        message.setText("Bonjour,\n\n" +
                "Votre code de vérification est : " + otp + "\n\n" +
                "Ce code expirera bientôt. Si vous n'avez pas demandé ce code, veuillez ignorer cet e-mail.\n\n" +
                "– Équipe Cooked");
        try {
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send email to " + to + ": " + e.getMessage());
        }
    }
}
