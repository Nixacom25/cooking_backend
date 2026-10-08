package com.cooked.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Invite a team member.
 *
 * @param role   ADMIN or EDITOR (data intern)
 * @param appUrl the backoffice origin, used for the set-password link when BACKOFFICE_URL is not configured
 */
public record TeamInviteRequest(
        @NotBlank @Email @Size(max = 160) String email,
        @Size(max = 80) String firstname,
        @Size(max = 80) String lastname,
        @NotBlank @Pattern(regexp = "ADMIN|EDITOR") String role,
        @Pattern(regexp = "^(https://|http://localhost).*", message = "appUrl must be an https URL") @Size(max = 200) String appUrl) {
}
