package com.cooked.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Test push to one account's device (e.g. the admin's own phone signed in to the app). */
public record TestPushRequest(@NotBlank @Email String email, @NotBlank @Size(max = 120) String title, @NotBlank @Size(max = 500) String body) {
}
