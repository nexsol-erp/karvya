package com.karvya.store.web;

import com.karvya.store.application.admin.AdminCategoryService;
import com.karvya.store.application.admin.AdminCustomerService;
import com.karvya.store.application.admin.AdminSettingsService;
import com.karvya.store.application.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Settings, categories and customers.
 *
 * <p>Grouped because each is small and they are administered together; the
 * services behind them stay separate.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin settings", description = "Site settings, categories and customer accounts")
public class AdminSettingsController {

    private final AdminSettingsService settings;
    private final AdminCategoryService categories;
    private final AdminCustomerService customers;

    public AdminSettingsController(AdminSettingsService settings, AdminCategoryService categories,
                                   AdminCustomerService customers) {
        this.settings = settings;
        this.categories = categories;
        this.customers = customers;
    }

    // ---- settings ---------------------------------------------------------

    @GetMapping("/settings")
    @Operation(summary = "Every setting, with its type and whether it is still a placeholder")
    public List<AdminSettingsService.SettingView> listSettings() {
        return settings.listAll();
    }

    /**
     * Saves a batch of settings.
     *
     * <p>Validated as a whole before anything is written, so one bad field
     * leaves nothing half-applied. Rich text is sanitised on the way in.
     */
    @PutMapping("/settings")
    @Operation(summary = "Update settings")
    public List<AdminSettingsService.SettingView> updateSettings(
            @Valid @RequestBody AdminSettingsService.Update request) {
        return settings.update(request.values(), actor());
    }

    /**
     * Sends a message to the signed-in administrator using the current mail
     * settings.
     *
     * <p>Without this the only way to learn that SMTP is wrong is to wait for a
     * customer to place an order and not receive anything. The outbox hides
     * delivery failures from the shopper by design, which is right for them and
     * useless for whoever is configuring it.
     */
    @PostMapping("/settings/mail/test")
    @Operation(summary = "Send a test email to the signed-in administrator")
    public Map<String, Object> sendTestEmail() {
        return settings.sendTestEmail(CurrentUserArgument.require().getEmail());
    }

    // ---- categories -------------------------------------------------------

    @GetMapping("/categories")
    @Operation(summary = "All categories, active or not")
    public List<AdminCategoryService.CategoryView> listCategories() {
        return categories.listAll();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a category")
    public AdminCategoryService.CategoryView createCategory(
            @Valid @RequestBody AdminCategoryService.Upsert request) {
        return categories.create(request, actor());
    }

    @PutMapping("/categories/{id}")
    @Operation(summary = "Update a category")
    public AdminCategoryService.CategoryView updateCategory(
            @PathVariable Long id, @Valid @RequestBody AdminCategoryService.Upsert request) {
        return categories.update(id, request, actor());
    }

    @PatchMapping("/categories/{id}/active")
    @Operation(summary = "Show or hide a category on the storefront")
    public AdminCategoryService.CategoryView setCategoryActive(
            @PathVariable Long id, @RequestParam boolean active) {
        return categories.setActive(id, active, actor());
    }

    // ---- customers --------------------------------------------------------

    @GetMapping("/customers")
    @Operation(summary = "Search registered customers")
    public PageResponse<AdminCustomerService.CustomerRow> listCustomers(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return customers.search(q, page, size);
    }

    @GetMapping("/customers/{id}")
    @Operation(summary = "One customer, with their recent orders")
    public AdminCustomerService.CustomerDetail customerDetail(@PathVariable Long id) {
        return customers.find(id);
    }

    @PatchMapping("/customers/{id}/enabled")
    @Operation(summary = "Enable or disable sign-in without touching order history")
    public AdminCustomerService.CustomerRow setCustomerEnabled(
            @PathVariable Long id, @RequestParam boolean enabled) {
        return customers.setEnabled(id, enabled, actor());
    }

    /**
     * Sends a reset link to the customer.
     *
     * <p>Returns nothing but a 202. The administrator never sees the token,
     * and cannot set the password themselves - the customer chooses it.
     */
    @PostMapping("/customers/{id}/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Email the customer a password reset link")
    public Map<String, String> sendPasswordReset(@PathVariable Long id) {
        customers.sendPasswordReset(id, actor());
        return Map.of("status", "A reset link has been queued for the customer.");
    }

    private String actor() {
        return CurrentUserArgument.require().getEmail();
    }
}
