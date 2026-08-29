package com.karvya.store.web;

import com.karvya.store.application.admin.AdminProductService;
import com.karvya.store.application.admin.dto.AdminProductDtos;
import com.karvya.store.application.common.PageResponse;
import com.karvya.store.domain.model.ProductStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Catalogue management.
 *
 * <p>There is no delete. A product that has been sold is still referenced by
 * order history, so leaving the catalogue is a status change to ARCHIVED - a
 * decision made once, here, rather than argued about at every call site.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin catalogue", description = "Product and image management")
public class AdminProductController {

    private final AdminProductService productAdmin;

    public AdminProductController(AdminProductService productAdmin) {
        this.productAdmin = productAdmin;
    }

    @GetMapping("/products")
    @Operation(summary = "List products in every status")
    public PageResponse<AdminProductDtos.Row> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productAdmin.list(q, status, page, size);
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "One product, with its gallery and lock version")
    public AdminProductDtos.Detail detail(@PathVariable Long id) {
        return productAdmin.find(id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a product")
    public AdminProductDtos.Detail create(@Valid @RequestBody AdminProductDtos.Upsert request) {
        return productAdmin.create(request, actor());
    }

    @PutMapping("/products/{id}")
    @Operation(summary = "Update a product; a stale version is refused")
    public AdminProductDtos.Detail update(@PathVariable Long id,
                                          @Valid @RequestBody AdminProductDtos.Upsert request) {
        return productAdmin.update(id, request, actor());
    }

    @PatchMapping("/products/{id}/status")
    @Operation(summary = "Activate, deactivate or archive a product")
    public AdminProductDtos.Detail changeStatus(@PathVariable Long id,
                                                @Valid @RequestBody AdminProductDtos.StatusChange request) {
        return productAdmin.changeStatus(id, request.status(), actor());
    }

    /** Whether the product has ever been sold, so the interface can warn first. */
    @GetMapping("/products/{id}/usage")
    @Operation(summary = "Whether this product appears in any order")
    public Map<String, Boolean> usage(@PathVariable Long id) {
        return Map.of("hasBeenOrdered", productAdmin.hasBeenOrdered(id));
    }

    /**
     * Uploads a photograph.
     *
     * <p>Validated for size, extension, magic bytes and pixel dimensions before
     * anything reaches disk, and stored under a generated name - the client
     * never chooses where its file lands.
     */
    @PostMapping(value = "/products/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a photograph to a product")
    public AdminProductDtos.Detail addImage(@PathVariable Long id,
                                            @RequestPart("file") MultipartFile file,
                                            @RequestPart(value = "altText", required = false) String altText) {
        return productAdmin.addImage(id, file, altText, actor());
    }

    @PutMapping("/products/{id}/images/order")
    @Operation(summary = "Reorder the gallery and choose which image leads it")
    public AdminProductDtos.Detail reorderImages(@PathVariable Long id,
                                                 @Valid @RequestBody AdminProductDtos.ImageOrder request) {
        return productAdmin.reorderImages(id, request.imageIds(), request.primaryImageId(), actor());
    }

    @DeleteMapping("/products/{id}/images/{imageId}")
    @Operation(summary = "Remove a photograph")
    public AdminProductDtos.Detail deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        return productAdmin.deleteImage(id, imageId, actor());
    }

    private String actor() {
        return CurrentUserArgument.require().getEmail();
    }
}
