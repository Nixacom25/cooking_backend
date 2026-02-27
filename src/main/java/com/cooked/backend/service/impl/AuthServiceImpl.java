package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.*;
import com.cooked.backend.dto.response.AuthResponse;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.entity.Provider;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.Status;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.EmailAlreadyExistsException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.BlacklistedTokenRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.repository.UserSubscriptionRepository;
import com.cooked.backend.security.JwtService;
import com.cooked.backend.service.AuthService;
import com.cooked.backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final AuthenticationManager authenticationManager;
        private final EmailService emailService;
        private final BlacklistedTokenRepository blacklistedTokenRepository;
        private final UserSubscriptionRepository userSubscriptionRepository;

        @Override
        public Object register(RegisterRequest request) {
                if (userRepository.existsByEmail(request.getEmail())) {
                        throw new EmailAlreadyExistsException("Email already exists");
                }
                if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
                        throw new BadRequestException("Phone number already exists");
                }

                Provider provider = Provider.LOCAL;
                if (request.getProvider() != null && !request.getProvider().trim().isEmpty()) {
                        try {
                                provider = Provider.valueOf(request.getProvider().toUpperCase());
                        } catch (IllegalArgumentException e) {
                                provider = Provider.LOCAL;
                        }
                }

                if (provider == Provider.LOCAL) {
                        if (request.getPassword() == null || request.getPassword().isEmpty()) {
                                throw new BadRequestException("Password is required for local registration");
                        }
                        if (request.getPhone() == null || request.getPhone().isEmpty()) {
                                throw new BadRequestException("Phone is required for local registration");
                        }
                } else {
                        if (request.getFirstname() == null || request.getFirstname().isEmpty()) {
                                throw new BadRequestException(
                                                "Firstname is required for " + provider.name() + " registration");
                        }
                        if (request.getLastname() == null || request.getLastname().isEmpty()) {
                                throw new BadRequestException(
                                                "Lastname is required for " + provider.name() + " registration");
                        }
                }

                String password = request.getPassword() != null && !request.getPassword().isEmpty()
                                ? request.getPassword()
                                : java.util.UUID.randomUUID().toString();

                String otp = generateOtp();

                var user = User.builder()
                                .firstname(request.getFirstname())
                                .lastname(request.getLastname())
                                .phone(request.getPhone())
                                .email(request.getEmail())
                                .password(passwordEncoder.encode(password))
                                .photo(request.getPhoto())
                                .provider(provider)
                                .role(Role.CLIENT)
                                .status(provider == Provider.LOCAL ? Status.PENDING_VERIFICATION : Status.ACTIVE)
                                .otpCode(provider == Provider.LOCAL ? otp : null)
                                .otpExpiration(provider == Provider.LOCAL ? LocalDateTime.now().plusMinutes(15) : null)
                                .resendCount(0)
                                .build();

                userRepository.save(user);

                com.cooked.backend.entity.UserSubscription trialSubscription = com.cooked.backend.entity.UserSubscription
                                .builder()
                                .user(user)
                                .startDate(LocalDateTime.now())
                                .endDate(LocalDateTime.now().plusDays(30))
                                .status(com.cooked.backend.entity.SubscriptionStatus.TRIAL)
                                .isYearly(false)
                                .build();
                userSubscriptionRepository.save(trialSubscription);

                if (provider == Provider.LOCAL) {
                        emailService.sendOtpEmail(user.getEmail(), otp);
                        return new MessageResponse("User registered successfully. Please verify your email.");
                } else {
                        String token = jwtService.generateToken(user.getEmail());
                        return new AuthResponse(token);
                }
        }

        @Override
        public AuthResponse verifyEmail(VerifyEmailRequest request) {
                User user;
                if (request.getIdentifier().contains("@")) {
                        user = userRepository.findByEmail(request.getIdentifier())
                                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                } else {
                        user = userRepository.findByPhone(request.getIdentifier())
                                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                }

                if (user.getStatus() != Status.PENDING_VERIFICATION) {
                        throw new BadRequestException("Email already verified or account blocked");
                }

                if (user.getOtpExpiration().isBefore(LocalDateTime.now())
                                || !user.getOtpCode().equals(request.getOtpCode())) {
                        throw new BadRequestException("Invalid or expired OTP");
                }

                user.setStatus(Status.ACTIVE);
                user.setOtpCode(null);
                user.setOtpExpiration(null);
                user.setResendCount(0);
                user.setLockoutUntil(null);
                userRepository.save(user);

                String token = jwtService.generateToken(user.getEmail());
                return new AuthResponse(token);
        }

        @Override
        public MessageResponse resendCode(ResendCodeRequest request) {
                User user;
                if (request.getIdentifier().contains("@")) {
                        user = userRepository.findByEmail(request.getIdentifier())
                                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                } else {
                        user = userRepository.findByPhone(request.getIdentifier())
                                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                }

                if (user.getLockoutUntil() != null && user.getLockoutUntil().isBefore(LocalDateTime.now())) {
                        user.setLockoutUntil(null);
                        user.setResendCount(0);
                }

                if (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(LocalDateTime.now())) {
                        throw new BadRequestException("Too many attempts. Please try again later.");
                }

                if (user.getStatus() == Status.ACTIVE) {
                        throw new BadRequestException("Email already verified");
                }

                String otp = generateOtp();
                user.setOtpCode(otp);
                user.setOtpExpiration(LocalDateTime.now().plusMinutes(15));
                user.setResendCount(user.getResendCount() != null ? user.getResendCount() + 1 : 1);

                if (user.getResendCount() >= 3) {
                        user.setLockoutUntil(LocalDateTime.now().plusHours(1));
                        user.setResendCount(0);
                }

                userRepository.save(user);

                emailService.sendOtpEmail(user.getEmail(), otp);

                return new MessageResponse("A new OTP code has been sent");
        }

        @Override
        public AuthResponse login(LoginRequest request) {
                User user;

                if (request.getIdentifier().contains("@")) {
                        user = userRepository.findByEmail(request.getIdentifier())
                                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                } else {
                        user = userRepository.findByPhone(request.getIdentifier())
                                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                }

                if (user.getStatus() != Status.ACTIVE) {
                        throw new BadRequestException("Please verify your account first or account is not active");
                }

                if ("GOOGLE".equalsIgnoreCase(request.getProvider())
                                || "APPLE".equalsIgnoreCase(request.getProvider())) {
                        // Skip password check for simulated OAuth
                        String token = jwtService.generateToken(user.getEmail());
                        return new AuthResponse(token);
                }

                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(user.getEmail(), request.getPassword()));

                String token = jwtService.generateToken(user.getEmail());
                return new AuthResponse(token);
        }

        @Override
        public MessageResponse logout(String token) {
                if (!blacklistedTokenRepository.existsByToken(token)) {
                        com.cooked.backend.entity.BlacklistedToken blacklistedToken = com.cooked.backend.entity.BlacklistedToken
                                        .builder()
                                        .token(token)
                                        .blacklistedAt(LocalDateTime.now())
                                        .build();
                        blacklistedTokenRepository.save(blacklistedToken);
                }
                return new MessageResponse("Logged out successfully");
        }

        @Override
        public MessageResponse forgotPassword(ForgotPasswordRequest request) {
                User user;
                if (request.getIdentifier().contains("@")) {
                        user = userRepository.findByEmail(request.getIdentifier())
                                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                } else {
                        user = userRepository.findByPhone(request.getIdentifier())
                                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                }

                if (user.getLockoutUntil() != null && user.getLockoutUntil().isBefore(LocalDateTime.now())) {
                        user.setLockoutUntil(null);
                        user.setResendCount(0);
                }

                if (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(LocalDateTime.now())) {
                        throw new BadRequestException("Too many attempts. Please try again later.");
                }

                String otp = generateOtp();
                user.setOtpCode(otp);
                user.setOtpExpiration(LocalDateTime.now().plusMinutes(15));
                user.setResendCount(user.getResendCount() != null ? user.getResendCount() + 1 : 1);

                if (user.getResendCount() >= 3) {
                        user.setLockoutUntil(LocalDateTime.now().plusHours(1));
                        user.setResendCount(0);
                }

                userRepository.save(user);

                emailService.sendOtpEmail(user.getEmail(), otp);

                return new MessageResponse("Password reset OTP sent");
        }

        @Override
        public MessageResponse verifyResetCode(VerifyResetCodeRequest request) {
                User user;
                if (request.getIdentifier().contains("@")) {
                        user = userRepository.findByEmail(request.getIdentifier())
                                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                } else {
                        user = userRepository.findByPhone(request.getIdentifier())
                                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                }

                if (user.getOtpCode() == null || user.getOtpExpiration().isBefore(LocalDateTime.now())
                                || !user.getOtpCode().equals(request.getOtpCode())) {
                        throw new BadRequestException("Invalid or expired OTP");
                }

                // Store a confirmed status in the OTP code field securely
                user.setOtpCode("VERIFIED");
                // Give them 15 minutes to use the reset window
                user.setOtpExpiration(LocalDateTime.now().plusMinutes(15));
                userRepository.save(user);

                return new MessageResponse("Code verified successfully. You can now reset your password.");
        }

        @Override
        public MessageResponse resetPassword(ResetPasswordRequest request) {
                User user;
                if (request.getIdentifier().contains("@")) {
                        user = userRepository.findByEmail(request.getIdentifier())
                                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                } else {
                        user = userRepository.findByPhone(request.getIdentifier())
                                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                }

                if (!"VERIFIED".equals(user.getOtpCode()) || user.getOtpExpiration().isBefore(LocalDateTime.now())) {
                        throw new BadRequestException("Password reset not authorized or window expired");
                }

                user.setPassword(passwordEncoder.encode(request.getNewPassword()));
                user.setOtpCode(null);
                user.setOtpExpiration(null);
                user.setResendCount(0);
                user.setLockoutUntil(null);
                userRepository.save(user);

                return new MessageResponse("Password reset successfully");
        }

        private String generateOtp() {
                Random random = new Random();
                int otp = 100000 + random.nextInt(900000);
                return String.valueOf(otp);
        }
}
