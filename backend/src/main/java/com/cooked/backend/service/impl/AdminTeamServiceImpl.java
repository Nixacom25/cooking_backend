package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateUserRequest;
import com.cooked.backend.dto.request.TeamInviteRequest;
import com.cooked.backend.dto.response.TeamActivityResponse;
import com.cooked.backend.dto.response.UserResponse;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.EmailService;
import com.cooked.backend.service.AdminTeamService;
import com.cooked.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminTeamServiceImpl implements AdminTeamService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserService userService;
    private final UserRepository users;
    private final EmailService emailService;

    @Value("${backoffice.url:}")
    private String backofficeUrl;

    @Override
    @Transactional
    public UserResponse invite(TeamInviteRequest r) {
        CreateUserRequest create = new CreateUserRequest();
        create.setEmail(r.email().trim().toLowerCase(Locale.ROOT));
        create.setFirstname(blankToNull(r.firstname()));
        create.setLastname(blankToNull(r.lastname()));
        create.setRole(Role.valueOf(r.role()));
        // Never sent anywhere: the member chooses their own password through "forgot password".
        create.setPassword(randomPassword());
        UserResponse created = userService.createAdmin(create);
        String base = backofficeUrl != null && !backofficeUrl.isBlank() ? backofficeUrl : (r.appUrl() == null ? "" : r.appUrl());
        String link = base.replaceAll("/+$", "") + "/forgot-password";
        emailService.sendTeamInviteEmail(create.getEmail(), create.getFirstname(), "ADMIN".equals(r.role()) ? "Admin" : "Data intern", link);
        return created;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamActivityResponse> activity() {
        Map<UUID, LocalDateTime> latestSession = new HashMap<>();
        for (Object[] row : users.findLastSessionByRoles(List.of(Role.ADMIN, Role.EDITOR))) {
            latestSession.put((UUID) row[0], (LocalDateTime) row[1]);
        }
        return users.findAllByRoleIn(List.of(Role.ADMIN, Role.EDITOR)).stream()
                .map(u -> new TeamActivityResponse(u.getId(), u.getEmail(), latest(latestSession.get(u.getId()), u.getLastActive())))
                .toList();
    }

    static LocalDateTime latest(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    static String randomPassword() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "aA1!";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
