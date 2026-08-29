package com.karvya.store.infrastructure.mail;

import com.karvya.store.application.settings.SettingsService;
import com.karvya.store.infrastructure.config.AppProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Where the SMTP connection details come from.
 *
 * <p>Settings first, environment second. Mail used to be environment-only, read
 * once at boot, so an owner who wanted to start sending order confirmations had
 * to edit a file on the server and restart - which is not something the person
 * running a shop can reasonably do. Configuring it in the admin now takes effect
 * on the next send.
 *
 * <p>A deployment already configured through {@code MAIL_*} keeps working
 * untouched: the settings start empty, and empty means "fall back".
 */
@Component
public class MailSenderProvider {

    private final SettingsService settings;
    private final AppProperties properties;
    private final JavaMailSender configured;

    public MailSenderProvider(SettingsService settings, AppProperties properties,
                              JavaMailSender configured) {
        this.settings = settings;
        this.properties = properties;
        this.configured = configured;
    }

    /** True when an administrator has supplied a server in site settings. */
    public boolean isConfiguredInSettings() {
        return settings.find(SettingsService.MAIL_HOST).isPresent();
    }

    /** The address messages are sent from, whichever source is in use. */
    public String from() {
        return settings.find(SettingsService.MAIL_FROM).orElse(properties.mailFrom());
    }

    /**
     * A sender for the current configuration.
     *
     * <p>Built per call rather than cached. A {@link JavaMailSenderImpl} holds
     * no connection - it opens one per send - so this costs nothing, and it
     * removes the question of when a cache would have to be invalidated after
     * an administrator changes the password.
     */
    public JavaMailSender get() {
        return settings.find(SettingsService.MAIL_HOST)
                .map(this::fromSettings)
                .orElse(configured);
    }

    private JavaMailSender fromSettings(String host) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(settings.find(SettingsService.MAIL_PORT).map(Integer::parseInt).orElse(587));
        settings.find(SettingsService.MAIL_USERNAME).ifPresent(sender::setUsername);
        settings.find(SettingsService.MAIL_PASSWORD).ifPresent(sender::setPassword);
        sender.setDefaultEncoding("UTF-8");

        Properties mail = sender.getJavaMailProperties();
        mail.put("mail.smtp.auth", settings.getBoolean(SettingsService.MAIL_AUTH, true));
        mail.put("mail.smtp.starttls.enable",
                settings.getBoolean(SettingsService.MAIL_STARTTLS, true));
        // A hung SMTP server must not hold the notification worker. The outbox
        // retries, so giving up quickly costs nothing and blocking costs the
        // whole queue.
        mail.put("mail.smtp.connectiontimeout", "5000");
        mail.put("mail.smtp.timeout", "5000");
        mail.put("mail.smtp.writetimeout", "5000");

        return sender;
    }
}
