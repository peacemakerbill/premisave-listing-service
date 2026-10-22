package com.premisave.listing.service;

import com.premisave.listing.entity.EmailOutbox;
import com.premisave.listing.enums.EmailStatus;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Durable email sending. Emails are written to the email_outbox collection
 * first and sent from there, so an SMTP outage or a crash between "booking
 * confirmed" and "email sent" does not lose the notification.
 *
 * A message is claimed atomically before sending (its next-attempt time is
 * pushed out and its attempt count raised), so several instances can poll
 * the same collection without sending anything twice. A failed send is
 * retried with a growing delay, and marked DEAD after email.outbox.max-attempts.
 * Sent records are deleted after email.outbox.keep-sent-days.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOutboxService {

    private final MongoTemplate mongoTemplate;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${mail-from:no-reply@premisave.com}")
    private String fromAddress;

    @Value("${email.outbox.max-attempts:6}")
    private int maxAttempts;

    @Value("${email.outbox.retry-backoff-minutes:2}")
    private int retryBackoffMinutes;

    @Value("${email.outbox.keep-sent-days:30}")
    private int keepSentDays;

    @Value("${email.outbox.batch-size:50}")
    private int batchSize;

    /** Queues an email. Variables are reduced to types MongoDB stores faithfully. */
    public void enqueue(String to, String subject, String template, Map<String, Object> variables) {
        if (to == null || to.isBlank()) {
            log.warn("Skipped queuing email '{}': no recipient address available.", subject);
            return;
        }
        EmailOutbox mail = new EmailOutbox();
        mail.setToAddress(to);
        mail.setSubject(subject);
        mail.setTemplate(template);
        mail.setVariables(simplify(variables));
        mail.setCreatedAt(new Date());
        mail.setNextAttemptAt(new Date());
        mongoTemplate.insert(mail);
    }

    /** Sends everything that is due. Safe to call from several threads or instances at once. */
    public void dispatchDue() {
        for (int i = 0; i < batchSize; i++) {
            EmailOutbox mail = claimNext();
            if (mail == null) {
                return;
            }
            send(mail);
        }
    }

    @Scheduled(fixedDelayString = "${email.outbox.poll-ms:30000}", initialDelayString = "${email.outbox.poll-ms:30000}")
    public void poll() {
        try {
            dispatchDue();
        } catch (Exception e) {
            log.error("Email outbox poll failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private EmailOutbox claimNext() {
        Date now = new Date();
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("status").is(EmailStatus.PENDING),
                Criteria.where("nextAttemptAt").lte(now)))
                .with(Sort.by(Sort.Direction.ASC, "nextAttemptAt"));
        // The claim also acts as a short lease: if this instance dies mid-send, the message becomes due again.
        Update update = new Update()
                .inc("attempts", 1)
                .set("nextAttemptAt", new Date(now.getTime() + Duration.ofMinutes(5).toMillis()));
        return mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), EmailOutbox.class);
    }

    private void send(EmailOutbox mail) {
        try {
            Context context = new Context();
            if (mail.getVariables() != null) {
                context.setVariables(mail.getVariables());
            }
            String html = templateEngine.process(mail.getTemplate(), context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(mail.getToAddress());
            helper.setSubject(mail.getSubject());
            helper.setText(html, true);
            mailSender.send(message);

            Date now = new Date();
            mongoTemplate.updateFirst(
                    new Query(Criteria.where("_id").is(mail.getId())),
                    new Update().set("status", EmailStatus.SENT)
                            .set("sentAt", now)
                            .set("expireAt", new Date(now.getTime() + Duration.ofDays(keepSentDays).toMillis()))
                            .unset("lastError"),
                    EmailOutbox.class);
            log.info("Email sent: to={}, subject={}", mail.getToAddress(), mail.getSubject());
        } catch (Exception e) {
            String error = e.getClass().getSimpleName() + ": " + e.getMessage();
            Update update = new Update().set("lastError", error);
            if (mail.getAttempts() >= maxAttempts) {
                update.set("status", EmailStatus.DEAD);
                log.error("Email gave up after {} attempts: to={}, subject={}, error={}",
                        mail.getAttempts(), mail.getToAddress(), mail.getSubject(), error);
            } else {
                long wait = Duration.ofMinutes((long) retryBackoffMinutes * mail.getAttempts()).toMillis();
                update.set("nextAttemptAt", new Date(System.currentTimeMillis() + wait));
                log.warn("Email attempt {}/{} failed: to={}, subject={}, error={}",
                        mail.getAttempts(), maxAttempts, mail.getToAddress(), mail.getSubject(), error);
            }
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(mail.getId())), update, EmailOutbox.class);
        }
    }

    private static Map<String, Object> simplify(Map<String, Object> variables) {
        Map<String, Object> result = new HashMap<>();
        if (variables == null) {
            return result;
        }
        variables.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            if (value instanceof BigDecimal decimal) {
                result.put(key, decimal.toPlainString());
            } else if (value instanceof Enum<?> e) {
                result.put(key, e.name());
            } else if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                result.put(key, value);
            } else {
                result.put(key, value.toString());
            }
        });
        return result;
    }
}
