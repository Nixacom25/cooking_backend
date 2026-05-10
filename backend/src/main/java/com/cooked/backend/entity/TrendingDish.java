package com.cooked.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "trending_dishes")
public class TrendingDish {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String name;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public TrendingDish() {}
    public TrendingDish(UUID id, String name, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public static TrendingDishBuilder builder() {
        return new TrendingDishBuilder();
    }

    public static class TrendingDishBuilder {
        private final TrendingDish dish = new TrendingDish();

        public TrendingDishBuilder id(UUID id) { dish.setId(id); return this; }
        public TrendingDishBuilder name(String name) { dish.setName(name); return this; }
        
        public TrendingDish build() {
            return dish;
        }
    }
}
