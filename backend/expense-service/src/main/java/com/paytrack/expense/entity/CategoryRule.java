package com.paytrack.expense.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "category_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryRule {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "keyword", nullable = false, length = 255)
    private String keyword;

    @Column(name = "category", nullable = false, length = 50)
    private String category; // FOOD, TRANSPORT, HOUSING, ENTERTAINMENT, UTILITIES, HEALTHCARE, SHOPPING, EDUCATION, OTHER

    @Column(name = "match_on", nullable = false, length = 20)
    @Builder.Default
    private String matchOn = "NOTE"; // NOTE, TXN_TYPE

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private int priority = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (matchOn == null) {
            matchOn = "NOTE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
