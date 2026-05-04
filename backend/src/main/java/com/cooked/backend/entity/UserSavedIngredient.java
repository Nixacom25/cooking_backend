package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_saved_ingredients")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSavedIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = true)
    private String icon;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public User getUser() { return user; }
    public String getName() { return name; }
    public String getIcon() { return icon; }
}
