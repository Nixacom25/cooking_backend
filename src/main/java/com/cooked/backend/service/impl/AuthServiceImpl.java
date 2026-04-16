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
import com.cooked.backend.service.ActivityLogService;
import com.cooked.backend.service.AuthService;
import com.cooked.backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import com.cooked.backend.entity.DeviceSession;
import com.cooked.backend.repository.DeviceSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.beans.factory.annotation.Value;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.Collections;

import lombok.extern.slf4j.Slf4j;

@Slf4j
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
        private final ActivityLogService activityLogService;
        private final DeviceSessionRepository deviceSessionRepository;

        @Value("${google.client.id:YOUR_GOOGLE_CLIENT_ID}")
        private String googleClientId;

        @Value("${apple.client.id:YOUR_APPLE_CLIENT_ID}")
        private String appleClientId;

        @Override
        @Transactional
        public Object register(RegisterRequest request) {
                if (userRepository.existsByEmail(request.getEmail())) {
                        throw new EmailAlreadyExistsException("Email already exists");
                }
                String phone = (request.getPhone() != null && !request.getPhone().trim().isEmpty())
                                ? request.getPhone().trim()
                                : null;

                if (phone != null && userRepository.existsByPhone(phone)) {
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

                String firstname = request.getFirstname();
                String lastname = request.getLastname();

                // Logic: If lastname is empty and firstname has multiple words, split it
                if ((lastname == null || lastname.trim().isEmpty()) && firstname != null && firstname.trim().contains(" ")) {
                        String trimmedName = firstname.trim();
                        int lastSpaceIndex = trimmedName.lastIndexOf(" ");
                        firstname = trimmedName.substring(0, lastSpaceIndex).trim();
                        lastname = trimmedName.substring(lastSpaceIndex).trim();
                }

                if (provider == Provider.LOCAL) {
                        if (request.getPassword() == null || request.getPassword().isEmpty()) {
                                throw new BadRequestException("Password is required for local registration");
                        }
                }

                String password = request.getPassword() != null && !request.getPassword().isEmpty()
                                ? request.getPassword()
                                : java.util.UUID.randomUUID().toString();

                String otp = generateOtp();

                var user = User.builder()
                                .firstname(firstname)
                                .lastname(lastname)
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
                                .discoverySource(request.getDiscoverySource())
                                .otherDiscoverySource(request.getOtherDiscoverySource())
                                .language(request.getLanguage())
                                .country(request.getCountry())
                                .alternativeRegion(request.getAlternativeRegion())
                                .measurementSystem(request.getMeasurementSystem())
                                .dietaryPreferences(request.getDietaryPreferences() != null
                                                ? request.getDietaryPreferences()
                                                : new java.util.ArrayList<>())
                                .allergies(request.getAllergies() != null ? request.getAllergies()
                                                : new java.util.ArrayList<>())
                                .foodDislikes(request.getFoodDislikes() != null ? request.getFoodDislikes()
                                                : new java.util.ArrayList<>())
                                .flavorDna(request.getFlavorDna() != null ? request.getFlavorDna()
                                                : new java.util.HashMap<>())
                                .spiceLevel(request.getSpiceLevel())
                                .cookingSkill(request.getCookingSkill())
                                .cookingTimePreference(request.getCookingTimePreference())
                                .cookingFrequency(request.getCookingFrequency())
                                .cookingTarget(request.getCookingTarget())
                                .favoriteCuisines(request.getFavoriteCuisines() != null ? request.getFavoriteCuisines()
                                                : new java.util.ArrayList<>())
                                .kitchenAppliances(
                                                request.getKitchenAppliances() != null ? request.getKitchenAppliances()
                                                                : new java.util.ArrayList<>())
                                .mealPlanningStyle(request.getMealPlanningStyle())
                                .notificationPreferences(request.getNotificationPreferences() != null
                                                ? request.getNotificationPreferences()
                                                : new java.util.ArrayList<>())
                                .onboardingGoals(request.getOnboardingGoals() != null ? request.getOnboardingGoals()
                                                : new java.util.ArrayList<>())
                                .onboardingRating(request.getOnboardingRating())
                                .onboardingFeedback(request.getOnboardingFeedback())
                                .build();

                User savedUser = userRepository.save(user);

                // Assign default trial
                assignTrial(savedUser);

                // Track Registration Activity
                activityLogService.logActivity(savedUser, "Account Created",
                                "Welcome to Cooked! Your account has been successfully created.");

                // If local, send verification email
                if (request.getProvider() == null || request.getProvider().equalsIgnoreCase(Provider.LOCAL.name())) {
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

                // 1. Handle Social Providers First (Auto-registration supported)
                if ("GOOGLE".equalsIgnoreCase(request.getProvider())) {
                        try {
                                GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(),
                                                new GsonFactory())
                                                .setAudience(Collections.singletonList(googleClientId))
                                                .build();

                                GoogleIdToken idToken = verifier.verify(request.getPassword()); // password field carries the idToken
                                if (idToken != null) {
                                        GoogleIdToken.Payload payload = idToken.getPayload();
                                        String email = payload.getEmail();

                                        user = userRepository.findByEmail(email).orElseGet(() -> {
                                                log.info("Creating new user for social login: {}", email);
                                                String firstname = (String) payload.get("given_name");
                                                String lastname = (String) payload.get("family_name");

                                                User newUser = User.builder()
                                                                .email(email)
                                                                .firstname(firstname != null && !firstname.isEmpty()
                                                                                ? firstname
                                                                                : "User")
                                                                .lastname(lastname != null && !lastname.isEmpty() ? lastname
                                                                                : "Cooked")
                                                                .provider(Provider.GOOGLE)
                                                                .status(Status.ACTIVE)
                                                                .role(Role.CLIENT)
                                                                .password(passwordEncoder
                                                                                .encode(java.util.UUID.randomUUID().toString()))
                                                                .build();
                                                User savedUser = userRepository.save(newUser);

                                                // Assign default trial for new social user
                                                assignTrial(savedUser);
                                                return savedUser;
                                        });
                                } else {
                                        log.error("Google Token verification returned null for email provided");
                                        throw new BadRequestException("Invalid Google Token");
                                }
                        } catch (Exception e) {
                                log.error("Google Authentication failed: {}", e.getMessage(), e);
                                throw new BadRequestException("Google Authentication failed: " + e.getMessage());
                        }

                        String token = jwtService.generateToken(user.getEmail());
                        activityLogService.logActivity(user, "Login Successful", "User logged in via Google");
                        recordSession(user, token);
                        return new AuthResponse(token);
                }

                if ("APPLE".equalsIgnoreCase(request.getProvider())) {
                        // Apple implementation... (omitted for brevity as it's currently a placeholder throwing error)
                        throw new BadRequestException(
                                        "Apple Login is currently being configured. Please use Google or Email.");
                }

                // 2. Handle Standard Login (Email/Phone + Password)
                if (request.getIdentifier() == null || request.getIdentifier().trim().isEmpty()) {
                        throw new BadRequestException("Email or phone is required");
                }

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

                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(user.getEmail(), request.getPassword()));

                String token = jwtService.generateToken(user.getEmail());
                activityLogService.logActivity(user, "Login Successful", "User logged in successfully.");
                recordSession(user, token);
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

                deviceSessionRepository.findByToken(token).ifPresent(deviceSessionRepository::delete);

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
                activityLogService.logActivity(user, "Forgot Password", "Password reset OTP requested.");

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
                activityLogService.logActivity(user, "Password Reset Code Verified",
                                "OTP for password reset successfully verified.");

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
                userRepository.save(user);

                activityLogService.logActivity(user, "Password Changed", "Your password has been successfully reset.");

                return new MessageResponse("Password reset successful.");
        }

        private String generateOtp() {
                Random random = new Random();
                int otp = 100000 + random.nextInt(900000);
                return String.valueOf(otp);
        }

        private void assignTrial(User user) {
                com.cooked.backend.entity.UserSubscription trialSubscription = com.cooked.backend.entity.UserSubscription
                                .builder()
                                .user(user)
                                .startDate(LocalDateTime.now())
                                .endDate(LocalDateTime.now().plusDays(3))
                                .status(com.cooked.backend.entity.SubscriptionStatus.TRIAL)
                                .isYearly(false)
                                .build();
                userSubscriptionRepository.save(trialSubscription);
        }

        private void recordSession(User user, String token) {
                try {
                        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder
                                        .getRequestAttributes();
                        if (attrs != null) {
                                HttpServletRequest req = attrs.getRequest();
                                String userAgent = req.getHeader("User-Agent");
                                String ipAddress = req.getHeader("X-Forwarded-For");
                                if (ipAddress == null || ipAddress.isEmpty()) {
                                        ipAddress = req.getRemoteAddr();
                                } else {
                                        ipAddress = ipAddress.split(",")[0].trim();
                                }

                                String deviceName = "Unknown Device";
                                if (userAgent != null) {
                                        if (userAgent.contains("iPhone"))
                                                deviceName = "iPhone";
                                        else if (userAgent.contains("Macintosh"))
                                                deviceName = "MacBook";
                                        else if (userAgent.contains("iPad"))
                                                deviceName = "iPad";
                                        else if (userAgent.contains("Android"))
                                                deviceName = "Android Device";
                                        else if (userAgent.contains("Windows"))
                                                deviceName = "Windows PC";
                                        else if (userAgent.contains("Linux"))
                                                deviceName = "Linux PC";
                                        else if (userAgent.contains("Dart") || userAgent.contains("Flutter"))
                                                deviceName = "Cooked Mobile App";
                                        else
                                                deviceName = userAgent.length() > 30
                                                                ? userAgent.substring(0, 30) + "..."
                                                                : userAgent;
                                }

                                DeviceSession session = DeviceSession.builder()
                                                .user(user)
                                                .deviceName(deviceName)
                                                .ipAddress(ipAddress != null ? ipAddress : "Unknown")
                                                .location("Unknown Location")
                                                .token(token)
                                                .build();

                                deviceSessionRepository.save(session);
                        }
                } catch (Exception e) {
                        // Ignore failure
                }
        }
}
