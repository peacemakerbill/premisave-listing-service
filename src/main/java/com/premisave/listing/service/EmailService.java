package com.premisave.listing.service;

import com.premisave.listing.entity.Booking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * All notification emails for bookings and listing interest, sent to both
 * parties (tenant/customer and listing owner) for every state change.
 *
 * @Async so a slow or failing SMTP call never blocks or breaks the
 * underlying action — by the time any of these run, the booking/refund/
 * interest record has already been saved, so a failed email is logged and
 * swallowed rather than propagated (see send()).
 *
 * Uses plain-text SimpleMailMessage rather than HTML templates for this
 * first pass — deliberately simple; swap in a templating engine
 * (Thymeleaf, FreeMarker) later if richer formatting is wanted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${mail-from}")
    private String fromAddress;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy");

    // ====================== BOOKING ======================

    @Async
    public void sendBookingConfirmedEmails(String tenantEmail, String tenantName,
                                            String ownerEmail, String ownerName,
                                            String listingTitle, Booking booking) {
        String dates = booking.getCheckIn().format(DATE_FORMAT) + " to " + booking.getCheckOut().format(DATE_FORMAT);

        send(tenantEmail,
            "Booking confirmed: " + listingTitle,
            greeting(tenantName) +
            "You have made a booking for \"" + listingTitle + "\" (" + dates + "). " +
            "Total charged: " + booking.getCurrency() + " " + booking.getTotalAmount() + ".\n\n" +
            "Thanks for booking with Premisave!"
        );

        if (ownerEmail != null) {
            send(ownerEmail,
                "New booking: " + listingTitle,
                greeting(ownerName) +
                (tenantName != null ? tenantName : "A customer") + " has booked \"" + listingTitle + "\" (" + dates + "). " +
                "Payment of " + booking.getCurrency() + " " + booking.getTotalAmount() + " has been sent to your wallet.\n\n" +
                "— Premisave"
            );
        }
    }

    @Async
    public void sendBookingCancelledEmails(String tenantEmail, String tenantName,
                                            String ownerEmail, String ownerName,
                                            String listingTitle, Booking booking) {
        send(tenantEmail,
            "Booking cancelled: " + listingTitle,
            greeting(tenantName) +
            "Your booking for \"" + listingTitle + "\" has been cancelled and " +
            booking.getCurrency() + " " + booking.getTotalAmount() + " has been refunded to your wallet.\n\n" +
            "— Premisave"
        );

        if (ownerEmail != null) {
            send(ownerEmail,
                "Booking cancelled: " + listingTitle,
                greeting(ownerName) +
                "The booking for \"" + listingTitle + "\" by " + (tenantName != null ? tenantName : "a customer") +
                " has been cancelled. " + booking.getCurrency() + " " + booking.getTotalAmount() +
                " has been refunded from your wallet.\n\n" +
                "— Premisave"
            );
        }
    }

    // ====================== INTEREST ======================

    @Async
    public void sendInterestExpressedEmails(String customerEmail, String customerName,
                                             String ownerEmail, String ownerName,
                                             String listingTitle) {
        send(customerEmail,
            "You expressed interest in: " + listingTitle,
            greeting(customerName) +
            "You are interested in \"" + listingTitle + "\". The owner has been notified and may reach out to you directly.\n\n" +
            "— Premisave"
        );

        if (ownerEmail != null) {
            send(ownerEmail,
                "New interest in your listing: " + listingTitle,
                greeting(ownerName) +
                (customerName != null ? customerName : "A customer") + " is interested in \"" + listingTitle + "\". " +
                "Check your listing's interest list for their contact details.\n\n" +
                "— Premisave"
            );
        }
    }

    @Async
    public void sendInterestCancelledEmails(String customerEmail, String customerName,
                                             String ownerEmail, String ownerName,
                                             String listingTitle) {
        send(customerEmail,
            "Interest cancelled: " + listingTitle,
            greeting(customerName) +
            "You are no longer interested in \"" + listingTitle + "\". Your contact details have been removed from this listing.\n\n" +
            "— Premisave"
        );

        if (ownerEmail != null) {
            send(ownerEmail,
                "Interest withdrawn: " + listingTitle,
                greeting(ownerName) +
                (customerName != null ? customerName : "A customer") + " is no longer interested in \"" + listingTitle + "\".\n\n" +
                "— Premisave"
            );
        }
    }

    // ====================== HELPERS ======================

    private String greeting(String name) {
        return "Hi " + (name != null && !name.isBlank() ? name : "there") + ",\n\n";
    }

    private void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            log.warn("Skipped sending email '{}' — no recipient address available.", subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent: to={}, subject={}", to, subject);
        } catch (Exception e) {
            // Never let an email failure break the underlying booking/interest
            // action — the money movement or record change has already
            // succeeded by the time this runs (@Async, called after the
            // fact), so a failed notification shouldn't roll anything back.
            log.error("Failed to send email: to={}, subject={}, error={}: {}",
                    to, subject, e.getClass().getSimpleName(), e.getMessage());
        }
    }
}