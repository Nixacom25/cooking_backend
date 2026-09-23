package com.cooked.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SupportTicketRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 120)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Subject is required")
    @Size(max = 150)
    private String subject;

    @NotBlank(message = "Message is required")
    @Size(max = 5000)
    private String message;

    /** "WEB" or "MOBILE" - defaults to WEB when omitted (the marketing site doesn't send it). */
    private String source;

    /** User ID (for mobile app submissions when user is logged in) */
    private String userId;

    /** Device/Platform information (e.g., "iOS", "Android", "Web") */
    private String platform;

    /** App version (for mobile app submissions) */
    private String appVersion;

    /** Category for better organization (Account, Payment, Scan, Import, Recipe, Shopping, Other) */
    private String category;
}
