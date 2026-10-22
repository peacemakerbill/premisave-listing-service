package com.premisave.listing.entity;

import com.premisave.listing.enums.EmailStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.Map;

/**
 * An email waiting to be sent, or already sent. Kept in MongoDB so a crash,
 * an SMTP outage or a restart never loses a notification.
 */
@Data
@Document(collection = "email_outbox")
@CompoundIndex(name = "status_next_attempt_idx", def = "{'status': 1, 'nextAttemptAt': 1}")
public class EmailOutbox {

    @Id
    private String id;

    private String toAddress;
    private String subject;
    private String template;

    /** Template variables: strings, numbers and booleans only. */
    private Map<String, Object> variables;

    private EmailStatus status = EmailStatus.PENDING;
    private int attempts;
    private Date nextAttemptAt;
    private String lastError;

    private Date createdAt;
    private Date sentAt;

    /** Set when sent; MongoDB deletes the record then (see EmailOutboxService). */
    @Indexed(expireAfter = "0s")
    private Date expireAt;
}
