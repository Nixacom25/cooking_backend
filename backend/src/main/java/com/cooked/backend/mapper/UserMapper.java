package com.cooked.backend.mapper;

import com.cooked.backend.dto.response.UserResponse;
import com.cooked.backend.entity.User;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.SubscriptionStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(source = "photo", target = "profilePictureUrl")
    @Mapping(target = "onboardingCompleted", expression = "java(UserMapper.onboardingCompleted(user))")
    UserResponse toResponse(User user);

    /**
     * An account "finished onboarding" once it reached its end (welcome
     * email sent), or ever had a subscription, or isn't a regular client.
     * Only an account created and then abandoned at the paywall is false.
     */
    static boolean onboardingCompleted(User user) {
        if (user.getRole() != null && user.getRole() != Role.CLIENT) return true;
        if (user.isWelcomeEmailSent()) return true;
        if (user.getSubscriptionStatus() != null && user.getSubscriptionStatus() != SubscriptionStatus.FREE) return true;
        return user.getSubscriptionExpiresAt() != null || user.getOriginalTransactionId() != null;
    }
}
