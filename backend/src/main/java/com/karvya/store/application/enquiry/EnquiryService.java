package com.karvya.store.application.enquiry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karvya.store.application.common.PageResponse;
import com.karvya.store.application.enquiry.dto.EnquiryDtos;
import com.karvya.store.application.settings.SettingsService;
import com.karvya.store.domain.NotFoundException;
import com.karvya.store.domain.model.ContactEnquiry;
import com.karvya.store.domain.model.EmailNotification;
import com.karvya.store.domain.model.EnquiryStatus;
import com.karvya.store.domain.repository.ContactEnquiryRepository;
import com.karvya.store.domain.repository.EmailNotificationRepository;
import com.karvya.store.infrastructure.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The contact form and its triage.
 *
 * <p>The message is written to the database first and the notification queued
 * to the outbox second, so a customer who has taken the trouble to write never
 * loses their message to a mail server outage.
 */
@Service
public class EnquiryService {

    private static final Logger log = LoggerFactory.getLogger(EnquiryService.class);

    private final ContactEnquiryRepository enquiries;
    private final EmailNotificationRepository notifications;
    private final SettingsService settings;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public EnquiryService(ContactEnquiryRepository enquiries, EmailNotificationRepository notifications,
                          SettingsService settings, AppProperties properties, ObjectMapper objectMapper) {
        this.enquiries = enquiries;
        this.notifications = notifications;
        this.settings = settings;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Accepts a submission.
     *
     * <p>A filled honeypot is discarded but still answered as a success. Saying
     * "you look like a bot" teaches the author to adjust and try again; saying
     * nothing at all costs them a retry they will not know to make.
     */
    @Transactional
    public void submit(EnquiryDtos.Submit request, String sourceIp) {
        if (request.looksAutomated()) {
            log.info("Discarded a contact submission that filled the honeypot, from {}", sourceIp);
            return;
        }

        ContactEnquiry enquiry = enquiries.save(ContactEnquiry.received(
                request.name(), request.email(), request.phone(),
                request.subject(), request.message(), sourceIp));

        String adminEmail = settings.find(SettingsService.ADMIN_EMAIL)
                .orElse(properties.adminNotificationEmail());

        if (adminEmail == null || adminEmail.isBlank()) {
            // the message is safely stored either way; it just waits in the
            // admin area until somebody configures an address
            log.warn("Enquiry {} saved but no administrator email is configured", enquiry.getId());
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", enquiry.getName());
        payload.put("email", enquiry.getEmail());
        payload.put("subjectLine", enquiry.getSubject());
        payload.put("message", enquiry.getMessage());

        notifications.save(EmailNotification.queue(
                EmailNotification.TYPE_ENQUIRY_ADMIN,
                adminEmail,
                "New enquiry: " + enquiry.getSubject(),
                json(payload)));

        log.info("Enquiry {} received from {}", enquiry.getId(), enquiry.getEmail());
    }

    @Transactional(readOnly = true)
    public PageResponse<EnquiryDtos.View> list(EnquiryStatus status, String q, int page, int size) {
        var pageable = PageRequest.of(Math.max(0, page), size <= 0 ? 20 : Math.min(size, 100));

        var results = (q != null && !q.isBlank())
                ? enquiries.search(q.trim(), pageable)
                : (status != null
                        ? enquiries.findByStatusOrderByCreatedAtDesc(status, pageable)
                        : enquiries.findAllByOrderByCreatedAtDesc(pageable));

        return PageResponse.from(results, EnquiryDtos.View::from);
    }

    @Transactional(readOnly = true)
    public EnquiryDtos.View find(Long id) {
        return EnquiryDtos.View.from(require(id));
    }

    @Transactional
    public EnquiryDtos.View updateStatus(Long id, EnquiryDtos.StatusChange request, String actor) {
        ContactEnquiry enquiry = require(id);
        enquiry.moveTo(request.status(), actor);
        if (request.internalNote() != null && !request.internalNote().isBlank()) {
            enquiry.setInternalNote(request.internalNote().trim(), actor);
        }
        return EnquiryDtos.View.from(enquiry);
    }

    private ContactEnquiry require(Long id) {
        return enquiries.findById(id)
                .orElseThrow(() -> new NotFoundException("Enquiry", String.valueOf(id)));
    }

    private String json(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise the notification payload", e);
        }
    }
}
