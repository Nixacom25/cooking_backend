package com.cooked.backend.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

/** Sent by the website's creator / ambassador forms. */
@Data
public class CreatorApplicationRequest {
    @NotBlank @Pattern(regexp = "AMBASSADOR|RECIPE_CREATOR")
    private String program;
    @NotBlank @Size(max = 120)
    private String name;
    @NotBlank @Email @Size(max = 255)
    private String email;
    @Size(max = 80)
    private String handle;
    @Size(max = 40)
    private String platform;
    @Size(max = 40)
    private String audience;
    @Size(max = 4000)
    private String details;
    /** Honeypot: must stay empty (bots fill every field). */
    @Size(max = 0)
    private String website;
}
