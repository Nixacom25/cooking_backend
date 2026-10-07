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
}
