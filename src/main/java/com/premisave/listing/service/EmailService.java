package com.premisave.listing.service;

import com.premisave.listing.entity.Booking;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * All notification emails for bookings and listing interest, sent to both
 * parties (tenant/customer and listing owner) for every state change.
 *
 * Renders HTML via Thymeleaf templates under
 * src/main/resources/templates/email/ — the TemplateEngine bean here is
 * the same one Spring Boot's Thymeleaf autoconfiguration already provides
 * for MVC views (added via spring-boot-starter-thymeleaf); reusing it for
 * email rendering is a standard pattern and needs no extra configuration,
 * since template resolution already defaults to classpath:/templates/
 * with an .html suffix — exactly where these templates live.
 *
 * Visual design matches wallet-service's own transactional email templates
 * (navy header, icon-badge headline, feature card, detail-row table,
 * footer) for a consistent look across Premisave's emails.
 *
 * @Async so a slow or failing SMTP call never blocks or breaks the
 * underlying action — by the time any of these run, the booking/refund/
 * interest record has already been saved, so a failed email is logged and
 * swallowed rather than propagated (see sendHtml()).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${mail-from:no-reply@premisave.com}")
    private String fromAddress;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy, h:mm a");

    // ====================== BOOKING ======================

    @Async
    public void sendBookingConfirmedEmails(String tenantEmail, String tenantName,
                                            String ownerEmail, String ownerName,
                                            String listingTitle, Booking booking) {
        String checkIn = booking.getCheckIn().format(DATE_FORMAT);
        String checkOut = booking.getCheckOut().format(DATE_FORMAT);
        String timestamp = now();
        String reference = booking.getPaymentReference();

        Context tenantCtx = new Context();
        tenantCtx.setVariable("recipientName", displayName(tenantName));
        tenantCtx.setVariable("listingTitle", listingTitle);
        tenantCtx.setVariable("checkIn", checkIn);
        tenantCtx.setVariable("checkOut", checkOut);
        tenantCtx.setVariable("currency", booking.getCurrency());
        tenantCtx.setVariable("totalAmount", booking.getTotalAmount());
        tenantCtx.setVariable("timestamp", timestamp);
        tenantCtx.setVariable("reference", reference);
        sendHtml(tenantEmail, "Booking confirmed: " + listingTitle, "email/booking-confirmed-tenant", tenantCtx);

        if (ownerEmail != null) {
            Context ownerCtx = new Context();
            ownerCtx.setVariable("recipientName", displayName(ownerName));
            ownerCtx.setVariable("otherPartyName", displayName(tenantName));
            ownerCtx.setVariable("listingTitle", listingTitle);
            ownerCtx.setVariable("checkIn", checkIn);
            ownerCtx.setVariable("checkOut", checkOut);
            ownerCtx.setVariable("currency", booking.getCurrency());
            ownerCtx.setVariable("totalAmount", booking.getTotalAmount());
            ownerCtx.setVariable("timestamp", timestamp);
            ownerCtx.setVariable("reference", reference);
            sendHtml(ownerEmail, "New booking: " + listingTitle, "email/booking-confirmed-owner", ownerCtx);
        }
    }

    @Async
    public void sendBookingCancelledEmails(String tenantEmail, String tenantName,
                                            String ownerEmail, String ownerName,
                                            String listingTitle, Booking booking) {
        String timestamp = now();
        String reference = booking.getRefundReference();

        Context tenantCtx = new Context();
        tenantCtx.setVariable("recipientName", displayName(tenantName));
        tenantCtx.setVariable("listingTitle", listingTitle);
        tenantCtx.setVariable("currency", booking.getCurrency());
        tenantCtx.setVariable("totalAmount", booking.getTotalAmount());
        tenantCtx.setVariable("timestamp", timestamp);
        tenantCtx.setVariable("reference", reference);
        sendHtml(tenantEmail, "Booking cancelled: " + listingTitle, "email/booking-cancelled-tenant", tenantCtx);

        if (ownerEmail != null) {
            Context ownerCtx = new Context();
            ownerCtx.setVariable("recipientName", displayName(ownerName));
            ownerCtx.setVariable("otherPartyName", displayName(tenantName));
            ownerCtx.setVariable("listingTitle", listingTitle);
            ownerCtx.setVariable("currency", booking.getCurrency());
            ownerCtx.setVariable("totalAmount", booking.getTotalAmount());
            ownerCtx.setVariable("timestamp", timestamp);
            ownerCtx.setVariable("reference", reference);
            sendHtml(ownerEmail, "Booking cancelled: " + listingTitle, "email/booking-cancelled-owner", ownerCtx);
        }
    }

    // ====================== INTEREST ======================

    @Async
    public void sendInterestExpressedEmails(String customerEmail, String customerName,
                                             String ownerEmail, String ownerName,
                                             String listingTitle) {
        String timestamp = now();

        Context customerCtx = new Context();
        customerCtx.setVariable("recipientName", displayName(customerName));
        customerCtx.setVariable("listingTitle", listingTitle);
        customerCtx.setVariable("timestamp", timestamp);
        sendHtml(customerEmail, "You expressed interest in: " + listingTitle, "email/interest-expressed-customer", customerCtx);

        if (ownerEmail != null) {
            Context ownerCtx = new Context();
            ownerCtx.setVariable("recipientName", displayName(ownerName));
            ownerCtx.setVariable("otherPartyName", displayName(customerName));
            ownerCtx.setVariable("listingTitle", listingTitle);
            ownerCtx.setVariable("timestamp", timestamp);
            sendHtml(ownerEmail, "New interest in your listing: " + listingTitle, "email/interest-expressed-owner", ownerCtx);
        }
    }

    @Async
    public void sendInterestCancelledEmails(String customerEmail, String customerName,
                                             String ownerEmail, String ownerName,
                                             String listingTitle) {
        String timestamp = now();

        Context customerCtx = new Context();
        customerCtx.setVariable("recipientName", displayName(customerName));
        customerCtx.setVariable("listingTitle", listingTitle);
        customerCtx.setVariable("timestamp", timestamp);
        sendHtml(customerEmail, "Interest cancelled: " + listingTitle, "email/interest-cancelled-customer", customerCtx);

        if (ownerEmail != null) {
            Context ownerCtx = new Context();
            ownerCtx.setVariable("recipientName", displayName(ownerName));
            ownerCtx.setVariable("otherPartyName", displayName(customerName));
            ownerCtx.setVariable("listingTitle", listingTitle);
            ownerCtx.setVariable("timestamp", timestamp);
            sendHtml(ownerEmail, "Interest withdrawn: " + listingTitle, "email/interest-cancelled-owner", ownerCtx);
        }
    }

    // ====================== HELPERS ======================

    private String now() {
        return LocalDateTime.now().format(TIMESTAMP_FORMAT);
    }

    private String displayName(String name) {
        return (name != null && !name.isBlank()) ? name : "there";
    }

    private void sendHtml(String to, String subject, String templateName, Context context) {
        if (to == null || to.isBlank()) {
            log.warn("Skipped sending email '{}' — no recipient address available.", subject);
            return;
        }
        try {
            String html = templateEngine.process(templateName, context);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(mimeMessage);
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