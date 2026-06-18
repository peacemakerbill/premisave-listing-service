package com.premisave.listing.entity;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data
public abstract class BaseEntity {

    @Id
    private String id;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Field("active")
    private boolean active = true;

    @Field("archived")
    private boolean archived = false;

    // Soft delete — record stays in DB but is invisible to users
    @Field("deleted")
    private boolean deleted = false;

    @Field("deleted_at")
    private LocalDateTime deletedAt;
}