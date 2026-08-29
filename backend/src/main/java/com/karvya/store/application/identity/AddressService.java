package com.karvya.store.application.identity;

import com.karvya.store.application.identity.dto.AddressDtos;
import com.karvya.store.domain.ConflictException;
import com.karvya.store.domain.NotFoundException;
import com.karvya.store.domain.model.AppUser;
import com.karvya.store.domain.model.CustomerAddress;
import com.karvya.store.domain.repository.AppUserRepository;
import com.karvya.store.domain.repository.CustomerAddressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
    private static final Logger log = LoggerFactory.getLogger(AddressService.class);

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

    /**
     * Saves the address a signed-in customer typed at checkout and makes it
     * their default, so the next order offers it instead of asking again.
     *
     * <p>Deliberately silent about every failure it can reach. This runs inside
     * the order's transaction, and an order must never be lost because the
     * address book could not be updated - the order is what the customer came
     * for, the saved address is a convenience. The only two things that could
     * go wrong are handled by returning rather than throwing: a full address
     * book, and an account that has since disappeared.
     *
     * <p>An address the customer already has is not duplicated; it is promoted
     * to default instead, so ordering to an old address twice does not fill the
     * list with copies of it.
     */
    @Transactional
    public void rememberFromCheckout(Long userId, AddressDtos.Request request) {
        List<CustomerAddress> existing =
                addresses.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId);

        Optional<CustomerAddress> match = existing.stream()
                .filter(address -> sameAddress(address, request))
                .findFirst();

        if (match.isPresent()) {
            setDefaultIfRequested(userId, match.get(), true);
            return;
        }

        if (existing.size() >= MAX_ADDRESSES_PER_USER) {
            log.info("Address book is full for account {}; checkout address not saved", userId);
            return;
        }

        AppUser user = users.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        CustomerAddress address = CustomerAddress.forUser(user);
        apply(address, request);
        // cleared and flushed before the insert, so the new default row never
        // meets the old one inside the same statement batch
        setDefaultIfRequested(userId, address, true);
        addresses.save(address);
    }

    /**
     * Promotes an address the customer chose at checkout, so the one they last
     * ordered to is the one offered next time.
     */
    @Transactional
    public void promoteToDefault(Long userId, Long addressId) {
        addresses.findByIdAndUserId(addressId, userId)
                .ifPresent(address -> setDefaultIfRequested(userId, address, true));
    }

    /**
     * Whether a stored address is the same place as one typed at checkout.
     *
     * <p>Compared on the fields that identify a destination, after folding case
     * and runs of whitespace, and with the phone reduced to its digits - so
     * "+91 97468 00113" and "9746800113" are not stored twice.
     */
    private static boolean sameAddress(CustomerAddress address, AddressDtos.Request request) {
        return norm(address.getRecipientName()).equals(norm(request.recipientName()))
                && digits(address.getPhone()).equals(digits(request.phone()))
                && norm(address.getLine1()).equals(norm(request.line1()))
                && norm(address.getLine2()).equals(norm(request.line2()))
                && norm(address.getCity()).equals(norm(request.city()))
                && norm(address.getState()).equals(norm(request.state()))
                && norm(address.getPostalCode()).equals(norm(request.postalCode()));
    }

    private static String norm(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
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
