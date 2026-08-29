package com.karvya.store.web;

import com.karvya.store.application.admin.AdminVendorService;
import com.karvya.store.application.admin.dto.AdminVendorDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Suppliers.
 *
 * <p>Under {@code /admin}, which the security configuration restricts to the
 * administrator role. There is no public equivalent and there should not be:
 * the price paid to a supplier and their contact details are the shop's
 * business, not the shopper's.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin: vendors", description = "Who supplies each product")
public class AdminVendorController {

    private final AdminVendorService vendors;

    public AdminVendorController(AdminVendorService vendors) {
        this.vendors = vendors;
    }

    @GetMapping("/vendors")
    @Operation(summary = "Every supplier, with how many products each supplies")
    public List<AdminVendorDtos.Row> list() {
        return vendors.list();
    }

    @GetMapping("/vendors/{id}")
    @Operation(summary = "One supplier")
    public AdminVendorDtos.Detail detail(@PathVariable Long id) {
        return vendors.find(id);
    }

    @PostMapping("/vendors")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a supplier")
    public AdminVendorDtos.Detail create(@Valid @RequestBody AdminVendorDtos.Upsert request) {
        return vendors.create(request, actor());
    }

    @PutMapping("/vendors/{id}")
    @Operation(summary = "Update a supplier")
    public AdminVendorDtos.Detail update(@PathVariable Long id,
                                         @Valid @RequestBody AdminVendorDtos.Upsert request) {
        return vendors.update(id, request, actor());
    }

    @DeleteMapping("/vendors/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a supplier that nothing is sourced from")
    public void delete(@PathVariable Long id) {
        vendors.delete(id, actor());
    }

    private String actor() {
        return CurrentUserArgument.require().getEmail();
    }
}
