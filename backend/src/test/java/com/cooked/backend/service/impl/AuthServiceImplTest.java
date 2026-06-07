package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.LoginRequest;
import com.cooked.backend.dto.request.RegisterRequest;
import com.cooked.backend.dto.response.AuthResponse;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.entity.Provider;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.Status;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.BlacklistedTokenRepository;
import com.cooked.backend.repository.DeviceSessionRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.repository.UserSubscriptionRepository;
import com.cooked.backend.security.JwtService;
import com.cooked.backend.service.ActivityLogService;
import com.cooked.backend.service.EmailService;
import com.cooked.backend.service.UserInitializationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private EmailService emailService;
    @Mock
    private BlacklistedTokenRepository blacklistedTokenRepository;
    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    @Mock
    private UserInitializationService userInitializationService;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private DeviceSessionRepository deviceSessionRepository;
    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AuthServiceImpl authService;

    private User dummyUser;

    private UUID dummyUserId;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ReflectionTestUtils.setField(authService, "googleClientId", "dummy-client-id");
        ReflectionTestUtils.setField(authService, "appleClientId", "dummy-apple-id");

        dummyUserId = java.util.UUID.randomUUID();
        dummyUser = User.builder()
                .id(dummyUserId)
                .email("test@example.com")
                .firstname("Test")
                .lastname("User")
                .password("encoded_password")
                .status(Status.ACTIVE)
                .provider(Provider.LOCAL)
                .role(Role.CLIENT)
                .build();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testRegister_LocalProvider_Success() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("newuser@example.com");
        request.setFirstname("New");
        request.setLastname("User");
        request.setPassword("password123");
        request.setProvider("LOCAL");

        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded123");
        
        User savedUser = User.builder().id(java.util.UUID.randomUUID()).email("newuser@example.com").build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        Object response = authService.register(request);

        assertTrue(response instanceof MessageResponse);
        assertEquals("User registered successfully. Please verify your email.", ((MessageResponse) response).getMessage());

        verify(userRepository, times(2)).save(any(User.class));
        verify(emailService).sendOtpEmail(eq("newuser@example.com"), anyString());
    }

    @Test
    void testRegister_EmailAlreadyExists_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> authService.register(request));
    }

    @Test
    void testLogin_LocalProvider_Success() {
        LoginRequest request = new LoginRequest();
        request.setIdentifier("test@example.com");
        request.setPassword("password123");
        request.setProvider("LOCAL");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(dummyUser));
        when(jwtService.generateToken("test@example.com")).thenReturn("dummy-jwt-token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("dummy-jwt-token", response.getToken());

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(activityLogService).logActivity(any(), anyString(), anyString());
    }

    @Test
    void testLogin_LocalProvider_UnverifiedUser() {
        dummyUser.setStatus(Status.PENDING_VERIFICATION);
        LoginRequest request = new LoginRequest();
        request.setIdentifier("test@example.com");
        request.setPassword("password123");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(dummyUser));

        assertThrows(BadRequestException.class, () -> authService.login(request));
    }

    // Google Token validation is difficult to mock because it uses new GoogleIdTokenVerifier.Builder() inside the method.
    // Instead of testing the internal Google logic which will fail without a real token/mock framework,
    // we assume Social Login testing will be handled at the integration layer. We can test standard flows here.
}
