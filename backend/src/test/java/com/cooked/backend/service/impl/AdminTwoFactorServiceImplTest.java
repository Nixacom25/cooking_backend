package com.cooked.backend.service.impl;

import com.cooked.backend.entity.AdminTwoFactorCode;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.AdminTwoFactorCodeRepository;
import com.cooked.backend.security.JwtService;
import com.cooked.backend.service.EmailService;
import com.cooked.backend.service.WorkspaceSettingsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminTwoFactorServiceImplTest {

    private final AdminTwoFactorCodeRepository codes = mock(AdminTwoFactorCodeRepository.class);
    private final EmailService email = mock(EmailService.class);
    private final JwtService jwt = mock(JwtService.class);
    private final AdminTwoFactorServiceImpl service = new AdminTwoFactorServiceImpl(codes, email, jwt, mock(WorkspaceSettingsService.class));

    @Test
    void sendsHashedCodeAndVerifiesOnce() {
        when(codes.findFirstByEmailAndUsedFalseOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());
        service.sendCode("admin@cooked.app");
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(email).sendOtpEmail(eq("admin@cooked.app"), sent.capture());
        ArgumentCaptor<AdminTwoFactorCode> saved = ArgumentCaptor.forClass(AdminTwoFactorCode.class);
        verify(codes).save(saved.capture());
        assertNotEquals(sent.getValue(), saved.getValue().getCodeHash());           // never stored in clear
        assertEquals(6, sent.getValue().length());

        when(codes.findFirstByEmailAndUsedFalseOrderByCreatedAtDesc("admin@cooked.app")).thenReturn(Optional.of(saved.getValue()));
        when(jwt.generateToken(eq(Map.of("mfa", true)), eq("admin@cooked.app"))).thenReturn("mfa-token");
        assertThrows(BadRequestException.class, () -> service.verify("admin@cooked.app", "000000".equals(sent.getValue()) ? "111111" : "000000"));
        assertEquals("mfa-token", service.verify("admin@cooked.app", sent.getValue()));
        assertTrue(saved.getValue().isUsed());
    }

    @Test
    void expiryAttemptsAndRateLimit() {
        AdminTwoFactorCode old = AdminTwoFactorCode.builder().email("a@x.com").codeHash(AdminTwoFactorServiceImpl.hash("a@x.com", "123456"))
                .expiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(codes.findFirstByEmailAndUsedFalseOrderByCreatedAtDesc("a@x.com")).thenReturn(Optional.of(old));
        assertThrows(BadRequestException.class, () -> service.verify("a@x.com", "123456"));

        AdminTwoFactorCode tired = AdminTwoFactorCode.builder().email("b@x.com").codeHash(AdminTwoFactorServiceImpl.hash("b@x.com", "123456"))
                .expiresAt(LocalDateTime.now().plusMinutes(5)).attempts(5).build();
        when(codes.findFirstByEmailAndUsedFalseOrderByCreatedAtDesc("b@x.com")).thenReturn(Optional.of(tired));
        assertThrows(BadRequestException.class, () -> service.verify("b@x.com", "123456"));

        when(codes.countByEmailAndCreatedAtAfter(eq("c@x.com"), any())).thenReturn(5L);
        assertThrows(ResponseStatusException.class, () -> service.sendCode("c@x.com"));
        assertEquals("ad…@cooked.app", AdminTwoFactorServiceImpl.mask("admin@cooked.app"));
    }
}
