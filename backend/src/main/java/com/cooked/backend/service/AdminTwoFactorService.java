package com.cooked.backend.service;

import com.cooked.backend.dto.response.TwoFactorStatusResponse;

/** Email code second factor for admins (when required in Settings). */
public interface AdminTwoFactorService {

    TwoFactorStatusResponse status(String email, String token);

    /** Emails a new 6-digit code (max 5 per hour). */
    void sendCode(String email);

    /** Checks the code and returns a new token carrying the "mfa" claim. */
    String verify(String email, String code);
}
