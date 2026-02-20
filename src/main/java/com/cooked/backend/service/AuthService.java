package com.cooked.backend.service;

import com.cooked.backend.dto.request.LoginRequest;
import com.cooked.backend.dto.request.RegisterRequest;
import com.cooked.backend.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);

    AuthResponse authenticate(LoginRequest request);
}