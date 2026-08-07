package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.CreatorResponse;
import com.cooked.backend.dto.response.NotificationResponse;
import com.cooked.backend.entity.Notification;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.NotificationRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public void createAndSendNotification(User recipient, User sender, String title, String message, String type, UUID recipeId, UUID assignmentId) {
        if (recipient == null) return;

        Notification notification = Notification.builder()
                .recipient(recipient)
                .sender(sender)
                .title(title)
                .message(message)
                .type(type)
                .recipeId(recipeId)
                .assignmentId(assignmentId)
                .read(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        NotificationResponse response = mapToResponse(saved);

        // Push real-time WS notification to recipient's private topic
        try {
            messagingTemplate.convertAndSend("/topic/user/" + recipient.getId() + "/notifications", response);
        } catch (Exception e) {
            log.error("Failed to broadcast WS notification to user {}: {}", recipient.getId(), e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        return notificationRepository.findAllByRecipientIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        return notificationRepository.countByRecipientIdAndReadFalse(user.getId());
    }

    @Override
    @Transactional
    public void markAsRead(UUID notificationId, String userEmail) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        notificationRepository.markAllAsReadForUser(user.getId());
    }

    private NotificationResponse mapToResponse(Notification n) {
        CreatorResponse senderDto = null;
        if (n.getSender() != null) {
            senderDto = CreatorResponse.builder()
                    .id(n.getSender().getId())
                    .firstname(n.getSender().getFirstname())
                    .lastname(n.getSender().getLastname())
                    .email(n.getSender().getEmail())
                    .photo(n.getSender().getPhoto())
                    .build();
        }

        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .recipeId(n.getRecipeId())
                .assignmentId(n.getAssignmentId())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .sender(senderDto)
                .build();
    }
}
