package com.cooked.backend.service;

import com.cooked.backend.dto.response.ActivityLogResponse;
import com.cooked.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ActivityLogService {
    void logActivity(User user, String title, String message);

    Page<ActivityLogResponse> getMyActivities(String userEmail, Pageable pageable);
}
