package com.karvya.store.application.admin;

import com.karvya.store.application.admin.dto.AdminVendorDtos;
import com.karvya.store.domain.ConflictException;
import com.karvya.store.domain.NotFoundException;
import com.karvya.store.domain.model.Vendor;
import com.karvya.store.domain.repository.VendorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Suppliers. Back office only; nothing here is reachable from the storefront. */
@Service
public class AdminVendorService {

    private static final Logger log = LoggerFactory.getLogger(AdminVendorService.class);

    private final VendorRepository vendors;

    public AdminVendorService(VendorRepository vendors) {
        this.vendors = vendors;
    }

    @Transactional(readOnly = true)
    public List<AdminVendorDtos.Row> list() {
        return vendors.findAllByOrderByNameAsc().stream()
                .map(v -> new AdminVendorDtos.Row(v.getId(), v.getName(), v.getContactName(),
                        v.getEmail(), v.getPhone(), v.getDeliveryTime(), v.isActive(),
                        vendors.countProducts(v.getId()), v.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminVendorDtos.Detail find(Long id) {
        Vendor vendor = require(id);
        return AdminVendorDtos.Detail.from(vendor, vendors.countProducts(id));
    }

    @Transactional
    public AdminVendorDtos.Detail create(AdminVendorDtos.Upsert request, String actor) {
        Vendor vendor = Vendor.named(request.name());
        apply(vendor, request, actor);
        vendors.save(vendor);

        log.info("{} added supplier {}", actor, vendor.getName());
        return AdminVendorDtos.Detail.from(vendor, 0);
    }

    @Transactional
    public AdminVendorDtos.Detail update(Long id, AdminVendorDtos.Upsert request, String actor) {
        Vendor vendor = require(id);
        vendor.setName(request.name());
        apply(vendor, request, actor);
        vendors.saveAndFlush(vendor);

        log.info("{} updated supplier {}", actor, vendor.getName());
        return AdminVendorDtos.Detail.from(vendor, vendors.countProducts(id));
    }

    /**
     * Removes a supplier that nothing is sourced from.
     *
     * <p>Refused while products still name it. The foreign key would set those
     * to null and the deletion would look like it worked, leaving pieces with
     * no record of where they came from - and nothing to say what was lost.
     * Deactivating is the way to retire a supplier you still have history with.
     */
    @Transactional
    public void delete(Long id, String actor) {
        Vendor vendor = require(id);
        long used = vendors.countProducts(id);

        if (used > 0) {
            throw new ConflictException("vendor-in-use",
                    used + (used == 1 ? " product is" : " products are")
                            + " still supplied by " + vendor.getName()
                            + ". Move them to another supplier, or deactivate this one instead.");
        }

        vendors.delete(vendor);
        log.info("{} removed supplier {}", actor, vendor.getName());
    }

    private void apply(Vendor vendor, AdminVendorDtos.Upsert request, String actor) {
        vendor.setContactName(blankToNull(request.contactName()));
        vendor.setEmail(blankToNull(request.email()));
        vendor.setPhone(blankToNull(request.phone()));
        vendor.setAddress(blankToNull(request.address()));
        vendor.setDeliveryTime(blankToNull(request.deliveryTime()));
        vendor.setConditions(blankToNull(request.conditions()));
        vendor.setActive(request.active());
        vendor.setUpdatedBy(actor);
    }

    private Vendor require(Long id) {
        return vendors.findById(id)
                .orElseThrow(() -> new NotFoundException("Vendor", String.valueOf(id)));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
