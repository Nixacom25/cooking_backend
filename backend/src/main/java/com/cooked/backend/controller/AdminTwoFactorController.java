package com.cooked.backend.controller;

import com.cooked.backend.dto.request.TwoFactorVerifyRequest;
import com.cooked.backend.dto.response.TwoFactorStatusResponse;
import com.cooked.backend.service.AdminTwoFactorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Admin second factor (email code). HTTP only: the work is in {@link AdminTwoFactorService}. */
@RestController
@RequestMapping("/api/admin/2fa")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin 2FA", description = "Email code second factor for admins")
public class AdminTwoFactorController {

    private final AdminTwoFactorService twoFactor;

    @Operation(summary = "Is 2FA required, and is the current token verified?")
    @GetMapping("/status")
    public ResponseEntity<TwoFactorStatusResponse> status(Authentication auth, HttpServletRequest request) {
        return ResponseEntity.ok(twoFactor.status(auth.getName(), bearer(request)));
    }

    @Operation(summary = "Email a 6-digit code (valid 10 minutes, 5 per hour)")
    @PostMapping("/send")
    public ResponseEntity<Void> send(Authentication auth) {
        twoFactor.sendCode(auth.getName());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Confirm the code; returns a token to use from now on")
    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verify(@Valid @RequestBody TwoFactorVerifyRequest request, Authentication auth) {
        return ResponseEntity.ok(Map.of("token", twoFactor.verify(auth.getName(), request.getCode())));
    }

    private static String bearer(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        return h != null && h.startsWith("Bearer ") ? h.substring(7) : "";
    }
}
