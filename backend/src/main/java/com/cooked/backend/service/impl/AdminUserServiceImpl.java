package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.AdminUserFilter;
import com.cooked.backend.dto.response.UserResponse;
import com.cooked.backend.mapper.UserMapper;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.repository.spec.AdminUserSpecs;
import com.cooked.backend.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final com.cooked.backend.repository.UserActivityDayRepository activityRepository;

    @Override
    public Page<UserResponse> search(AdminUserFilter filter, Pageable pageable) {
        return userRepository.findAll(AdminUserSpecs.of(filter, LocalDateTime.now()), pageable).map(userMapper::toResponse);
    }

    @Override
    public List<String> sources() {
        return userRepository.findDistinctDiscoverySources();
    }

    @Override
    public java.util.Map<java.util.UUID, com.cooked.backend.dto.response.UserExtrasResponse> extras(java.util.Collection<java.util.UUID> ids) {
        java.util.Map<java.util.UUID, com.cooked.backend.dto.response.UserExtrasResponse> out = new java.util.LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) return out;
        java.util.Map<java.util.UUID, Object[]> latest = new java.util.HashMap<>();
        for (Object[] row : userRepository.findSessionsOf(ids)) latest.putIfAbsent((java.util.UUID) row[0], row);   // newest first
        java.util.Map<java.util.UUID, java.math.BigDecimal> revenue = new java.util.HashMap<>();
        for (Object[] row : userRepository.sumPaymentsOf(ids)) revenue.put((java.util.UUID) row[0], (java.math.BigDecimal) row[1]);
        for (java.util.UUID id : ids) {
            Object[] s = latest.get(id);
            out.put(id, new com.cooked.backend.dto.response.UserExtrasResponse(
                    s == null ? null : platformOf((String) s[1]), revenue.getOrDefault(id, java.math.BigDecimal.ZERO),
                    s == null ? null : (LocalDateTime) s[2]));
        }
        return out;
    }

    /** Display platform of a recorded device name. */
    static String platformOf(String deviceName) {
        String n = deviceName == null ? "" : deviceName.toLowerCase(java.util.Locale.ROOT);
        if (n.startsWith("ios")) return "iOS";
        if (n.startsWith("android")) return "Android";
        if (n.equals(com.cooked.backend.repository.spec.AdminUserSpecs.MOBILE_APP)) return "App";
        return n.isEmpty() ? null : "Web";
    }

    @Override
    public java.util.Map<String, List<String>> clientContextOptions() {
        List<String> versions = new java.util.ArrayList<>(userRepository.findDistinctAppVersions());
        versions.sort(java.util.Comparator.comparing(AdminUserServiceImpl::versionKey).reversed());
        return java.util.Map.of("versions", versions, "countries", userRepository.findDistinctCountries());
    }

    /** Sortable key of "1.0.5+107" (each part zero-padded). */
    static String versionKey(String v) {
        StringBuilder out = new StringBuilder();
        for (String part : v.split("[.+]")) out.append(String.format("%06d", part.chars().allMatch(Character::isDigit) && !part.isEmpty() ? Integer.parseInt(part) : 0));
        return out.toString();
    }

    @Override
    public com.cooked.backend.dto.response.UserSummaryResponse summary() {
        LocalDateTime now = LocalDateTime.now();
        var role = com.cooked.backend.entity.Role.CLIENT;
        return new com.cooked.backend.dto.response.UserSummaryResponse(
                userRepository.countByRole(role),
                activityRepository.countDistinctUsers(now.toLocalDate().minusDays(29), now.toLocalDate().plusDays(1)),
                userRepository.countByRoleAndSubscriptionStatusIn(role, List.of(com.cooked.backend.entity.SubscriptionStatus.TRIAL)),
                userRepository.countLapsedSince(List.of(com.cooked.backend.entity.SubscriptionStatus.CANCELLED, com.cooked.backend.entity.SubscriptionStatus.EXPIRED), now.minusDays(30)));
    }
}
