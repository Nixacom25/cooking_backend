package com.cooked.backend.controller;

import com.cooked.backend.dto.request.*;
import com.cooked.backend.dto.response.*;
import com.cooked.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a user")
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @Operation(summary = "Verify email")
    @PostMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(request));
    }

    @Operation(summary = "Resend code")
    @PostMapping("/resend-code")
    public ResponseEntity<MessageResponse> resendCode(@Valid @RequestBody ResendCodeRequest request) {
        return ResponseEntity.ok(authService.resendCode(request));
    }

    @Operation(summary = "Login")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Logout")
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        return ResponseEntity.ok(authService.logout(token));
    }

    @Operation(summary = "Forgot password")
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @Operation(summary = "Verify reset code")
    @PostMapping("/verify-reset-code")
    public ResponseEntity<MessageResponse> verifyResetCode(@Valid @RequestBody VerifyResetCodeRequest request) {
        return ResponseEntity.ok(authService.verifyResetCode(request));
    }

    @Operation(summary = "Reset password")
    @PostMapping("/reset-password")
    public ResponseEntity<AuthResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @Operation(summary = "Sign in with Apple web-auth redirect (Android only)",
            description = "Apple POSTs its authorization result here (form_post response_mode) "
                    + "for the Android web-auth flow, since Android has no native Sign in with "
                    + "Apple SDK. This just bounces the browser to the app's registered custom "
                    + "scheme so the sign_in_with_apple plugin's own Android activity can pick "
                    + "up the result - it never runs server-side auth logic itself.")
    @PostMapping(value = "/apple/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> appleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String id_token,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String error) {

        StringBuilder query = new StringBuilder();
        appendParam(query, "code", code);
        appendParam(query, "id_token", id_token);
        appendParam(query, "state", state);
        appendParam(query, "user", user);
        appendParam(query, "error", error);

        String intentUrl = "intent://callback" + query
                + "#Intent;package=com.cookedapp.app;scheme=com.cookedapp.app;end";

        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                + "<script>window.location.replace(" + toJsString(intentUrl) + ");</script>"
                + "</head><body>Redirecting back to Cooked&hellip;</body></html>";

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(html);
    }

    private void appendParam(StringBuilder query, String key, String value) {
        if (value == null || value.isEmpty()) return;
        query.append(query.length() == 0 ? '?' : '&')
                .append(key)
                .append('=')
                .append(java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8));
    }

    private String toJsString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
