package com.karvya.store.infrastructure.mail;

import com.karvya.store.application.notification.EmailSender;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Sends through the configured SMTP server.
 *
 * <p>Timeouts are set deliberately short in configuration. An unreachable mail
 * server must fail quickly and be retried, not tie up a worker thread for
 * minutes - the whole point of the outbox is that delivery is allowed to fail.
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final MailSenderProvider senders;

    public SmtpEmailSender(MailSenderProvider senders) {
        this.senders = senders;
    }

    @Override
    public void send(String to, String subject, String htmlBody) {
        try {
            // resolved per send, so a change in the admin takes effect on the
            // next message rather than at the next restart
            JavaMailSender mailSender = senders.get();

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, false, StandardCharsets.UTF_8.name());

            helper.setFrom(senders.from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.debug("Delivered '{}' to {}", subject, to);

        } catch (MailException | jakarta.mail.MessagingException e) {
            // the recipient is logged, the body is not - it carries order and
            // personal details that have no business in a log file
            throw new EmailDeliveryException(
                    "Could not deliver '" + subject + "' to " + to, e);
        }
    }
}
