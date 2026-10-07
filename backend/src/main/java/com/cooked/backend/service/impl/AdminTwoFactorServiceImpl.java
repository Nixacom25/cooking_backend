package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.TwoFactorStatusResponse;
import com.cooked.backend.entity.AdminTwoFactorCode;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.AdminTwoFactorCodeRepository;
import com.cooked.backend.security.JwtService;
import com.cooked.backend.service.AdminTwoFactorService;
import com.cooked.backend.service.EmailService;
import com.cooked.backend.service.WorkspaceSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminTwoFactorServiceImpl implements AdminTwoFactorService {

    static final int CODE_MINUTES = 10;
    static final int MAX_ATTEMPTS = 5;
    static final int MAX_CODES_PER_HOUR = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminTwoFactorCodeRepository codes;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final WorkspaceSettingsService settings;

    @Override
    public TwoFactorStatusResponse status(String email, String token) {
        return new TwoFactorStatusResponse(settings.current().isRequire2fa(), jwtService.hasMfa(token), mask(email));
    }

    @Override
    @Transactional
    public void sendCode(String email) {
        if (codes.countByEmailAndCreatedAtAfter(email, LocalDateTime.now().minusHours(1)) >= MAX_CODES_PER_HOUR) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many codes requested. Try again in an hour.");
        }
        codes.findFirstByEmailAndUsedFalseOrderByCreatedAtDesc(email).ifPresent(c -> { c.setUsed(true); codes.save(c); });
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codes.save(AdminTwoFactorCode.builder().email(email).codeHash(hash(email, code))
                .expiresAt(LocalDateTime.now().plusMinutes(CODE_MINUTES)).attempts(0).used(false).build());
        emailService.sendOtpEmail(email, code);
    }

    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public String verify(String email, String code) {
        AdminTwoFactorCode c = codes.findFirstByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new BadRequestException("Request a new code."));
        if (c.getExpiresAt().isBefore(LocalDateTime.now()) || c.getAttempts() >= MAX_ATTEMPTS) {
            c.setUsed(true);
            codes.save(c);
            throw new BadRequestException("This code has expired. Request a new one.");
        }
        c.setAttempts(c.getAttempts() + 1);
        if (!MessageDigest.isEqual(c.getCodeHash().getBytes(StandardCharsets.UTF_8), hash(email, code).getBytes(StandardCharsets.UTF_8))) {
            codes.save(c);
            throw new BadRequestException("Wrong code (" + (MAX_ATTEMPTS - c.getAttempts()) + " attempt(s) left).");
        }
        c.setUsed(true);
        codes.save(c);
        return jwtService.generateToken(Map.of("mfa", true), email);
    }

    static String hash(String email, String code) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((email.toLowerCase() + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String mask(String email) {
        if (email == null || !email.contains("@")) return null;
        String[] p = email.split("@", 2);
        return (p[0].length() <= 2 ? p[0].charAt(0) + "…" : p[0].substring(0, 2) + "…") + "@" + p[1];
    }
}
