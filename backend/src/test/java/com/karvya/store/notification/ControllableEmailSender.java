package com.karvya.store.notification;

import com.karvya.store.application.notification.EmailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A mail sender the test can break on demand.
 *
 * <p>The only honest way to show that an order survives an unreachable mail
 * server is to make the send genuinely fail, so this stands in for SMTP and
 * records what it was asked to deliver.
 */
public class ControllableEmailSender implements EmailSender {

    public record SentMessage(String to, String subject, String body) {
    }

    private final AtomicBoolean failing = new AtomicBoolean(false);
    private final List<SentMessage> sent = Collections.synchronizedList(new ArrayList<>());

    public void failEverything() {
        failing.set(true);
    }

    public void recover() {
        failing.set(false);
    }

    public void reset() {
        failing.set(false);
        sent.clear();
    }

    public List<SentMessage> sent() {
        return List.copyOf(sent);
    }

    @Override
    public void send(String to, String subject, String htmlBody) {
        if (failing.get()) {
            throw new EmailDeliveryException("Simulated SMTP outage for " + to,
                    new java.net.ConnectException("Connection refused"));
        }
        sent.add(new SentMessage(to, subject, htmlBody));
    }

    /**
     * Replaces the real SMTP sender wherever this configuration is imported.
     * Marked primary so nothing has to know it is being substituted.
     */
    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        public ControllableEmailSender controllableEmailSender() {
            return new ControllableEmailSender();
        }
    }
}
