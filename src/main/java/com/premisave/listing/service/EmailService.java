package com.premisave.listing.service;

import com.premisave.listing.entity.Booking;
import com.premisave.listing.enums.EscrowStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds the notification emails for bookings and listing interest, to both
 * parties for every state change, and hands them to EmailOutboxService,
 * which stores them and sends them with retries.
 *
 * Templates live under src/main/resources/templates/email/ and are rendered
 * at send time by the Thymeleaf engine Spring Boot already provides.
 *
 * @Async so the caller never waits on the database write or the first send
 * attempt. A failure here is logged and swallowed: by the time any of these
 * run, the booking or interest record has already been saved.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailOutboxService outbox;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy, h:mm a");

    // ====================== BOOKING ======================

    @Async
    public void sendBookingConfirmedEmails(String tenantEmail, String tenantName,
                                           String ownerEmail, String ownerName,
                                           String listingTitle, Booking booking,
                                           boolean escrow, int releaseDelayHours) {
        try {
            Map<String, Object> common = new HashMap<>();
            common.put("listingTitle", listingTitle);
            common.put("checkIn", booking.getCheckIn().format(DATE_FORMAT));
            common.put("checkOut", booking.getCheckOut().format(DATE_FORMAT));
            common.put("currency", booking.getCurrency());
            common.put("totalAmount", booking.getTotalAmount());
            common.put("timestamp", now());
            common.put("reference", booking.getPaymentReference());
            common.put("escrow", escrow);
            common.put("releaseDelayHours", releaseDelayHours);

            Map<String, Object> tenantVars = new HashMap<>(common);
            tenantVars.put("recipientName", displayName(tenantName));
            tenantVars.put("policy", booking.getCancellationPolicy());
            outbox.enqueue(tenantEmail, "Booking confirmed: " + listingTitle, "email/booking-confirmed-tenant", tenantVars);

            Map<String, Object> ownerVars = new HashMap<>(common);
            ownerVars.put("recipientName", displayName(ownerName));
            ownerVars.put("otherPartyName", displayName(tenantName));
            outbox.enqueue(ownerEmail, "New booking: " + listingTitle, "email/booking-confirmed-owner", ownerVars);

            outbox.dispatchDue();
        } catch (Exception e) {
            log.error("Could not queue booking confirmation emails: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    @Async
    public void sendBookingCancelledEmails(String tenantEmail, String tenantName,
                                           String ownerEmail, String ownerName,
                                           String listingTitle, Booking booking, boolean escrow) {
        try {
            boolean settled = !escrow || booking.getEscrowStatus() == EscrowStatus.SETTLED;
            BigDecimal refund = booking.getRefundAmount() != null ? booking.getRefundAmount() : booking.getTotalAmount();
            BigDecimal ownerShare = booking.getOwnerPayoutAmount() != null ? booking.getOwnerPayoutAmount() : BigDecimal.ZERO;
            int percent = booking.getRefundPercent() != null ? booking.getRefundPercent() : 100;

            Map<String, Object> common = new HashMap<>();
            common.put("listingTitle", listingTitle);
            common.put("currency", booking.getCurrency());
            common.put("totalAmount", booking.getTotalAmount());
            common.put("refundAmount", refund);
            common.put("refundPercent", percent);
            common.put("policy", booking.getCancellationPolicy());
            common.put("escrow", escrow);
            common.put("settled", settled);
            common.put("timestamp", now());
            common.put("reference", booking.getRefundReference());

            Map<String, Object> tenantVars = new HashMap<>(common);
            tenantVars.put("recipientName", displayName(tenantName));
            outbox.enqueue(tenantEmail, "Booking cancelled: " + listingTitle, "email/booking-cancelled-tenant", tenantVars);

            Map<String, Object> ownerVars = new HashMap<>(common);
            ownerVars.put("recipientName", displayName(ownerName));
            ownerVars.put("otherPartyName", displayName(tenantName));
            ownerVars.put("ownerAmount", ownerShare);
            ownerVars.put("ownerKeepsMoney", ownerShare.signum() > 0);
            outbox.enqueue(ownerEmail, "Booking cancelled: " + listingTitle, "email/booking-cancelled-owner", ownerVars);

            outbox.dispatchDue();
        } catch (Exception e) {
            log.error("Could not queue booking cancellation emails: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    // ====================== INTEREST ======================

    @Async
    public void sendInterestExpressedEmails(String customerEmail, String customerName,
                                            String ownerEmail, String ownerName,
                                            String listingTitle) {
        sendInterestEmails(customerEmail, customerName, ownerEmail, ownerName, listingTitle,
                "You expressed interest in: " + listingTitle, "email/interest-expressed-customer",
                "New interest in your listing: " + listingTitle, "email/interest-expressed-owner");
    }

    @Async
    public void sendInterestCancelledEmails(String customerEmail, String customerName,
                                            String ownerEmail, String ownerName,
                                            String listingTitle) {
        sendInterestEmails(customerEmail, customerName, ownerEmail, ownerName, listingTitle,
                "Interest cancelled: " + listingTitle, "email/interest-cancelled-customer",
                "Interest withdrawn: " + listingTitle, "email/interest-cancelled-owner");
    }

    private void sendInterestEmails(String customerEmail, String customerName,
                                    String ownerEmail, String ownerName, String listingTitle,
                                    String customerSubject, String customerTemplate,
                                    String ownerSubject, String ownerTemplate) {
        try {
            String timestamp = now();

            Map<String, Object> customerVars = new HashMap<>();
            customerVars.put("recipientName", displayName(customerName));
            customerVars.put("listingTitle", listingTitle);
            customerVars.put("timestamp", timestamp);
            outbox.enqueue(customerEmail, customerSubject, customerTemplate, customerVars);

            Map<String, Object> ownerVars = new HashMap<>();
            ownerVars.put("recipientName", displayName(ownerName));
            ownerVars.put("otherPartyName", displayName(customerName));
            ownerVars.put("listingTitle", listingTitle);
            ownerVars.put("timestamp", timestamp);
            outbox.enqueue(ownerEmail, ownerSubject, ownerTemplate, ownerVars);

            outbox.dispatchDue();
        } catch (Exception e) {
            log.error("Could not queue interest emails: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    // ====================== HELPERS ======================

    private String now() {
        return LocalDateTime.now().format(TIMESTAMP_FORMAT);
    }

    private String displayName(String name) {
        return (name != null && !name.isBlank()) ? name : "there";
    }
}
