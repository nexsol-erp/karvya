package com.karvya.store.web;

import com.karvya.store.application.admin.AdminAttributeService;
import com.karvya.store.application.admin.dto.AdminAttributeDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Product attributes an administrator defines. Back office only. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin: attributes", description = "Fields a product should have")
public class AdminAttributeController {

    private final AdminAttributeService attributes;

    public AdminAttributeController(AdminAttributeService attributes) {
        this.attributes = attributes;
    }

    @GetMapping("/attributes")
    @Operation(summary = "Every attribute definition")
    public List<AdminAttributeDtos.Row> list() {
        return attributes.list();
    }

    @GetMapping("/attributes/{id}")
    @Operation(summary = "One attribute definition")
    public AdminAttributeDtos.Detail detail(@PathVariable Long id) {
        return attributes.find(id);
    }

    @PostMapping("/attributes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Define an attribute")
    public AdminAttributeDtos.Detail create(@Valid @RequestBody AdminAttributeDtos.Upsert request) {
        return attributes.create(request, actor());
    }

    @PutMapping("/attributes/{id}")
    @Operation(summary = "Update a definition; the slug never changes")
    public AdminAttributeDtos.Detail update(@PathVariable Long id,
                                            @Valid @RequestBody AdminAttributeDtos.Upsert request) {
        return attributes.update(id, request, actor());
    }

    @DeleteMapping("/attributes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a definition nothing has answered")
    public void delete(@PathVariable Long id) {
        attributes.delete(id, actor());
    }

    private String actor() {
        return CurrentUserArgument.require().getEmail();
    }
}
