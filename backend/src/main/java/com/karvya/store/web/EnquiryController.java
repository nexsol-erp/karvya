package com.karvya.store.web;

import com.karvya.store.application.common.PageResponse;
import com.karvya.store.application.enquiry.EnquiryService;
import com.karvya.store.application.enquiry.dto.EnquiryDtos;
import com.karvya.store.domain.TooManyRequestsException;
import com.karvya.store.domain.model.EnquiryStatus;
import com.karvya.store.infrastructure.config.AppProperties;
import com.karvya.store.infrastructure.security.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * The contact form, and its triage in the back office.
 *
 * <p>Both live here because they are two views of one thing, and keeping the
 * public shape and the administrative shape side by side makes it obvious that
 * the internal note never appears in the public one.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Enquiries", description = "Contact form and enquiry management")
public class EnquiryController {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final EnquiryService enquiries;
    private final RateLimiter rateLimiter;
    private final AppProperties properties;

    public EnquiryController(EnquiryService enquiries, RateLimiter rateLimiter,
                             AppProperties properties) {
        this.enquiries = enquiries;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    /**
     * Accepts a message.
     *
     * <p>Always 202, whether the submission was stored or silently discarded as
     * automated. A different answer for the honeypot would tell a bot exactly
     * what to change.
     */
    @PostMapping("/enquiries")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Send a message through the contact form")
    public void submit(@Valid @RequestBody EnquiryDtos.Submit request, HttpServletRequest http) {
        String ip = clientIp(http);
        if (!rateLimiter.tryAcquire("enquiry:" + ip, properties.security().enquiriesPerHourPerIp(), WINDOW)) {
            throw new TooManyRequestsException();
        }
        enquiries.submit(request, ip);
    }

    // ---- back office ------------------------------------------------------

    @GetMapping("/admin/enquiries")
    @Operation(summary = "List and search enquiries")
    public PageResponse<EnquiryDtos.View> list(
            @RequestParam(required = false) EnquiryStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return enquiries.list(status, q, page, size);
    }

    @GetMapping("/admin/enquiries/{id}")
    @Operation(summary = "One enquiry in full")
    public EnquiryDtos.View detail(@PathVariable Long id) {
        return enquiries.find(id);
    }

    @PatchMapping("/admin/enquiries/{id}/status")
    @Operation(summary = "Triage an enquiry")
    public EnquiryDtos.View updateStatus(@PathVariable Long id,
                                         @Valid @RequestBody EnquiryDtos.StatusChange request) {
        return enquiries.updateStatus(id, request, CurrentUserArgument.require().getEmail());
    }

    /** Honours a single proxy hop; anything beyond it is client-supplied. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
