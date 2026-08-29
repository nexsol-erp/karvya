package com.karvya.store.application.identity;

import com.karvya.store.application.identity.dto.AddressDtos;
import com.karvya.store.domain.ConflictException;
import com.karvya.store.domain.NotFoundException;
import com.karvya.store.domain.model.AppUser;
import com.karvya.store.domain.model.CustomerAddress;
import com.karvya.store.domain.repository.AppUserRepository;
import com.karvya.store.domain.repository.CustomerAddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Saved delivery addresses.
 *
 * <p>Every method takes the owner's id and every lookup filters on it in the
 * query. Nothing here loads an address by id alone, so changing a number in a
 * URL returns "not found" rather than someone else's home address.
 */
@Service
public class AddressService {

    /** A cap, so an account cannot be used to accumulate unbounded rows. */
    private static final int MAX_ADDRESSES_PER_USER = 20;

    private final CustomerAddressRepository addresses;
    private final AppUserRepository users;

    public AddressService(CustomerAddressRepository addresses, AppUserRepository users) {
        this.addresses = addresses;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<AddressDtos.Response> list(Long userId) {
        return addresses.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId)
                .stream()
                .map(AddressDtos.Response::from)
                .toList();
    }

    @Transactional
    public AddressDtos.Response create(Long userId, AddressDtos.Request request) {
        if (addresses.countByUserId(userId) >= MAX_ADDRESSES_PER_USER) {
            throw new ConflictException("address-limit-reached",
                    "You have reached the maximum number of saved addresses.");
        }

        AppUser user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("Account", String.valueOf(userId)));

        CustomerAddress address = CustomerAddress.forUser(user);
        apply(address, request);

        // the first address saved becomes the default whether or not asked for
        boolean isFirst = addresses.countByUserId(userId) == 0;
        setDefaultIfRequested(userId, address, request.makeDefault() || isFirst);

        return AddressDtos.Response.from(addresses.save(address));
    }

    @Transactional
    public AddressDtos.Response update(Long userId, Long addressId, AddressDtos.Request request) {
        CustomerAddress address = require(userId, addressId);
        apply(address, request);
        setDefaultIfRequested(userId, address, request.makeDefault());
        return AddressDtos.Response.from(address);
    }

    @Transactional
    public void delete(Long userId, Long addressId) {
        CustomerAddress address = require(userId, addressId);
        boolean wasDefault = address.isDefaultAddress();
        addresses.delete(address);

        // never leave an account with addresses but no default
        if (wasDefault) {
            addresses.flush();
            addresses.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId).stream()
                    .findFirst()
                    .ifPresent(next -> next.setDefaultAddress(true));
        }
    }

    @Transactional
    public AddressDtos.Response makeDefault(Long userId, Long addressId) {
        CustomerAddress address = require(userId, addressId);
        setDefaultIfRequested(userId, address, true);
        return AddressDtos.Response.from(address);
    }

    private CustomerAddress require(Long userId, Long addressId) {
        return addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new NotFoundException("Address", String.valueOf(addressId)));
    }

    /**
     * A partial unique index enforces one default per account, so the previous
     * default must be cleared and flushed before the new one is set - otherwise
     * the two rows collide inside the same statement batch.
     */
    private void setDefaultIfRequested(Long userId, CustomerAddress address, boolean makeDefault) {
        if (!makeDefault || address.isDefaultAddress()) {
            return;
        }
        addresses.clearDefaultFor(userId);
        addresses.flush();
        address.setDefaultAddress(true);
    }

    private void apply(CustomerAddress address, AddressDtos.Request request) {
        address.setLabel(blankToNull(request.label()));
        address.setRecipientName(request.recipientName().trim());
        address.setPhone(request.phone().trim());
        address.setLine1(request.line1().trim());
        address.setLine2(blankToNull(request.line2()));
        address.setCity(request.city().trim());
        address.setState(request.state().trim());
        address.setPostalCode(request.postalCode().trim());
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
