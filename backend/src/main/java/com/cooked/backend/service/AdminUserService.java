package com.cooked.backend.service;

import com.cooked.backend.dto.request.AdminUserFilter;
import com.cooked.backend.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Admin Users table: filtered, paged search over app users. */
public interface AdminUserService {

    Page<UserResponse> search(AdminUserFilter filter, Pageable pageable);

    /** Distinct discovery sources answered in onboarding (filter options). */
    List<String> sources();

    /** Platform (latest login), lifetime revenue and last seen, per user id. */
    java.util.Map<java.util.UUID, com.cooked.backend.dto.response.UserExtrasResponse> extras(java.util.Collection<java.util.UUID> ids);

    com.cooked.backend.dto.response.UserSummaryResponse summary();

    /** {"versions": [...newest first], "countries": [...]} reported by the app. */
    java.util.Map<String, List<String>> clientContextOptions();
}
