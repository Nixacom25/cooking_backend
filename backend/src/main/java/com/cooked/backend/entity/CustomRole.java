package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A role between Admin and Data Intern: per area, None / View / Edit.
 * Assigned to ADMIN team members (User.customRoleId); enforced on every
 * request by {@link com.cooked.backend.security.CustomRoleFilter}.
 */
@Entity
@Table(name = "custom_roles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(length = 300)
    private String description;

    /** "users=VIEW,recipes=EDIT,…" (missing area = NONE). */
    @Column(nullable = false, length = 400)
    private String permissions;

    @Column(length = 160)
    private String createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
