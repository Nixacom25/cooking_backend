package com.cooked.backend.service;

import com.cooked.backend.dto.request.*;
import com.cooked.backend.dto.response.AuthResponse;
import com.cooked.backend.dto.response.MessageResponse;

public interface AuthService {
    Object register(RegisterRequest request);

    AuthResponse verifyEmail(VerifyEmailRequest request);

    MessageResponse resendCode(ResendCodeRequest request);

    AuthResponse login(LoginRequest request);

    MessageResponse logout(String token);

    MessageResponse forgotPassword(ForgotPasswordRequest request);

    MessageResponse verifyResetCode(VerifyResetCodeRequest request);

    AuthResponse resetPassword(ResetPasswordRequest request);
}