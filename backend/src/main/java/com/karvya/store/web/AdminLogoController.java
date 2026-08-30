package com.karvya.store.web;

import com.karvya.store.application.admin.AdminSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * The shop's logo.
 *
 * <p>Not part of the settings form: it is a file rather than a value, so it is
 * uploaded and removed rather than typed. The key it produces is stored in
 * store.logo_key, which is what the storefront reads.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin: branding", description = "The shop's logo")
public class AdminLogoController {

    private final AdminSettingsService settings;

    public AdminLogoController(AdminSettingsService settings) {
        this.settings = settings;
    }

    @PostMapping(value = "/settings/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace the logo")
    public Map<String, String> upload(@RequestPart("file") MultipartFile file) {
        return Map.of("logoKey", settings.replaceLogo(file, actor()));
    }

    @DeleteMapping("/settings/logo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove the logo; the shop falls back to its name")
    public void remove() {
        settings.removeLogo(actor());
    }

    private String actor() {
        return CurrentUserArgument.require().getEmail();
    }
}
