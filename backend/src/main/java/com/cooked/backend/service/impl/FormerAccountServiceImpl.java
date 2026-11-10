package com.cooked.backend.service.impl;

import com.cooked.backend.entity.FormerAccount;
import com.cooked.backend.repository.FormerAccountRepository;
import com.cooked.backend.service.FormerAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FormerAccountServiceImpl implements FormerAccountService {

    private final FormerAccountRepository repository;

    @Override
    @Transactional
    public void remember(String email) {
        String hash = hash(email);
        if (hash != null && !repository.existsByEmailHash(hash)) {
            repository.save(FormerAccount.builder().emailHash(hash).build());
        }
    }

    @Override
    public boolean hadAccountBefore(String email) {
        String hash = hash(email);
        return hash != null && repository.existsByEmailHash(hash);
    }

    /** SHA-256 of the email, lower-cased and trimmed; Gmail dots and +tags are ignored so aliases match. */
    static String hash(String email) {
        if (email == null || !email.contains("@")) return null;
        String e = email.trim().toLowerCase(Locale.ROOT);
        int at = e.indexOf('@');
        String local = e.substring(0, at), domain = e.substring(at + 1);
        int plus = local.indexOf('+');
        if (plus > 0) local = local.substring(0, plus);
        if (domain.equals("gmail.com") || domain.equals("googlemail.com")) {
            local = local.replace(".", "");
            domain = "gmail.com";
        }
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((local + "@" + domain).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
