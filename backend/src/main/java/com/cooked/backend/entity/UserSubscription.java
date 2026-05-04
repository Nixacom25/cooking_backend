package com.cooked.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "user_subscriptions")
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(nullable = false)
    private Boolean isYearly = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
    public SubscriptionStatus getStatus() { return status; }
    public void setStatus(SubscriptionStatus status) { this.status = status; }
    public Boolean getIsYearly() { return isYearly; }
    public void setIsYearly(Boolean isYearly) { this.isYearly = isYearly; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public static UserSubscriptionBuilder builder() {
        return new UserSubscriptionBuilder();
    }

    public static class UserSubscriptionBuilder {
        private final UserSubscription subscription = new UserSubscription();

        public UserSubscriptionBuilder id(UUID id) { subscription.setId(id); return this; }
        public UserSubscriptionBuilder user(User user) { subscription.setUser(user); return this; }
        public UserSubscriptionBuilder startDate(LocalDateTime startDate) { subscription.setStartDate(startDate); return this; }
        public UserSubscriptionBuilder endDate(LocalDateTime endDate) { subscription.setEndDate(endDate); return this; }
        public UserSubscriptionBuilder status(SubscriptionStatus status) { subscription.setStatus(status); return this; }
        public UserSubscriptionBuilder isYearly(Boolean isYearly) { subscription.setIsYearly(isYearly); return this; }

        public UserSubscription build() {
            return subscription;
        }
    }
}
