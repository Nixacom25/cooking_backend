package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TwoFactorStatusResponse {
    /** 2FA is required for admins (Settings). */
    private boolean required;
    /** The current token already carries a confirmed code. */
    private boolean verified;
    /** Where the code is sent (masked). */
    private String sentTo;
}
