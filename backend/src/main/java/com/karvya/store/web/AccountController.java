package com.karvya.store.web;

import com.karvya.store.application.identity.AddressService;
import com.karvya.store.application.identity.CustomerAccountService;
import com.karvya.store.application.identity.dto.AddressDtos;
import com.karvya.store.application.identity.dto.ProfileDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The signed-in customer's own data.
 *
 * <p>No endpoint here takes a user id. The owner always comes from the
 * security context, so there is no identifier a caller could change to reach
 * another account - the class of bug that turns a profile page into a data
 * breach.
 */
@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "Account", description = "Profile and saved addresses for the signed-in customer")
public class AccountController {

    private final CustomerAccountService accounts;
    private final AddressService addresses;

    public AccountController(CustomerAccountService accounts, AddressService addresses) {
        this.accounts = accounts;
        this.addresses = addresses;
    }

    @GetMapping("/profile")
    @Operation(summary = "Your profile")
    public ProfileDtos.Response getProfile() {
        return accounts.getProfile(CurrentUserArgument.requireUserId());
    }

    @PutMapping("/profile")
    @Operation(summary = "Update your name and phone number")
    public ProfileDtos.Response updateProfile(@Valid @RequestBody ProfileDtos.Request request) {
        return accounts.updateProfile(CurrentUserArgument.requireUserId(), request);
    }

    @GetMapping("/addresses")
    @Operation(summary = "Your saved delivery addresses")
    public List<AddressDtos.Response> listAddresses() {
        return addresses.list(CurrentUserArgument.requireUserId());
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save a delivery address")
    public AddressDtos.Response createAddress(@Valid @RequestBody AddressDtos.Request request) {
        return addresses.create(CurrentUserArgument.requireUserId(), request);
    }

    @PutMapping("/addresses/{addressId}")
    @Operation(summary = "Update one of your addresses")
    public AddressDtos.Response updateAddress(@PathVariable Long addressId,
                                              @Valid @RequestBody AddressDtos.Request request) {
        return addresses.update(CurrentUserArgument.requireUserId(), addressId, request);
    }

    @PostMapping("/addresses/{addressId}/default")
    @Operation(summary = "Make one of your addresses the default")
    public AddressDtos.Response makeDefault(@PathVariable Long addressId) {
        return addresses.makeDefault(CurrentUserArgument.requireUserId(), addressId);
    }

    @DeleteMapping("/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete one of your addresses")
    public void deleteAddress(@PathVariable Long addressId) {
        addresses.delete(CurrentUserArgument.requireUserId(), addressId);
    }
}
