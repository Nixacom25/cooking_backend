package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/** One row per (unmatched name, user) so the queue can show how many people hit it. */
@Entity
@Table(name = "unmatched_ingredient_users",
        uniqueConstraints = @UniqueConstraint(name = "uk_unmatched_user", columnNames = {"unmatched_id", "user_email"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnmatchedIngredientUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "unmatched_id", nullable = false)
    private UUID unmatchedId;

    @Column(name = "user_email", nullable = false, length = 160)
    private String userEmail;
}
