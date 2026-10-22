package com.premisave.listing.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "scheduler_locks")
public class SchedulerLock {

    @Id
    private String name;

    private Date lockedUntil;
    private String lockedBy;
    private Date lockedAt;
}
