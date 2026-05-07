package com.cooked.backend.security;

import com.cooked.backend.service.ActivityTracker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class ActivityInterceptor implements HandlerInterceptor {

    private final ActivityTracker activityTracker;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Update activity timestamp on every request
        activityTracker.updateActivity();
        return true;
    }
}
