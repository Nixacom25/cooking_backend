package com.cooked.backend.service;

import com.cooked.backend.dto.response.NotificationResponse;
import com.cooked.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {
    void createAndSendNotification(User recipient, User sender, String title, String message, String type, UUID recipeId, UUID assignmentId);
    Page<NotificationResponse> getUserNotifications(String userEmail, Pageable pageable);
    long getUnreadCount(String userEmail);
    void markAsRead(UUID notificationId, String userEmail);
    void markAllAsRead(String userEmail);
}
