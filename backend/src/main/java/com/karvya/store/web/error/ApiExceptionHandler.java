package com.karvya.store.web.error;

import com.karvya.store.domain.DomainException;
import com.karvya.store.domain.NotFoundException;
import com.karvya.store.domain.TooManyRequestsException;
import com.karvya.store.application.order.CheckoutValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates exceptions into RFC 7807 problem responses.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} rather than starting from
 * nothing. That base class already maps the whole family of Spring MVC
 * exceptions - unknown static resource, unsupported method, unreadable body -
 * onto their correct statuses. Without it, a bare
 * {@code @ExceptionHandler(Exception.class)} swallows all of them and reports
 * 500, so a missing image would look like a server fault.
 *
 * <p>Two rules hold throughout: the client is told what it can act on and
 * nothing more, and anything genuinely unexpected is logged in full
 * server-side while the response stays generic. Stack traces, SQL, and entity
 * names never cross the wire.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String PROBLEM_BASE = "https://karvya.example/problems/";

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Not found",
                "The requested resource does not exist.", ex.code(), request);
    }

    /**
     * Declared before the general DomainException handler it extends, because
     * a throttled caller needs 429 - the status that tells a client to back
     * off - not the 422 every other domain failure returns.
     */
    @ExceptionHandler(TooManyRequestsException.class)
    public ProblemDetail handleTooManyRequests(TooManyRequestsException ex, HttpServletRequest request) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts",
                ex.getMessage(), ex.code(), request);
    }

    /**
     * A cart that changed under the customer during checkout. 409 rather than
     * 422: the request was well formed and would have succeeded a moment
     * earlier, and the response carries the specific corrections so the
     * checkout page can show what happened instead of a generic failure.
     */
    @ExceptionHandler(CheckoutValidationException.class)
    public ProblemDetail handleCheckoutChanged(CheckoutValidationException ex,
                                               HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Your cart changed",
                ex.getMessage(), ex.code(), request);
        problem.setProperty("adjustments", ex.adjustments());
        return problem;
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Request could not be completed",
                ex.getMessage(), ex.code(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                            HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid parameter",
                "The value supplied for '" + ex.getName() + "' is not valid.",
                "invalid-parameter", request);
    }

    /**
     * Body validation failures, enriched with a field-to-message map so the
     * form can mark the offending inputs rather than showing one banner.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "One or more fields are invalid.");
        problem.setTitle("Validation failed");
        problem.setType(URI.create(PROBLEM_BASE + "validation-failed"));
        problem.setProperty("timestamp", Instant.now().toString());

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        problem.setProperty("errors", errors);

        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong",
                "The request could not be completed. Please try again.", "internal-error", request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail,
                                  String code, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(PROBLEM_BASE + code));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
