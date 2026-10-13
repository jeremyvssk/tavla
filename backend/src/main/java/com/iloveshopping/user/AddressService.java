// The shopper's saved addresses: at most ten, one default for delivery and one for billing, checked like the checkout's.
package com.iloveshopping.user;

import com.iloveshopping.auth.exception.AccessConflictException;
import com.iloveshopping.exception.FieldValidationException;
import com.iloveshopping.order.CheckoutRules;
import com.iloveshopping.user.dto.AddressRequest;
import com.iloveshopping.user.dto.AddressResponse;
import com.iloveshopping.user.exception.AddressNotFoundException;
import com.iloveshopping.user.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Every call is for the signed-in account only: an address is looked up by its id and the owner's id
 * together, so another account's id is "not found". Writes lock the account's row first, so two tabs saving
 * at once can't make an eleventh address or two defaults (the partial unique indexes would refuse that too).
 * Each write answers with the whole list, so the page shows the defaults as they are now.
 */
@Service
public class AddressService {

    static final int MAX_ADDRESSES = 10;

    private final AddressRepository addresses;
    private final UserRepository users;

    public AddressService(AddressRepository addresses, UserRepository users) {
        this.addresses = addresses;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(UUID userId) {
        return addresses.findByUserIdOrderByCreatedAtAscIdAsc(userId).stream().map(AddressService::view).toList();
    }

    /** The first address becomes both defaults: there is always a default while there is an address. */
    @Transactional
    public List<AddressResponse> create(UUID userId, AddressRequest request) {
        lock(userId);
        long count = addresses.countByUserId(userId);
        if (count >= MAX_ADDRESSES) {
            throw new AccessConflictException("address_limit");
        }
        Address address = new Address();
        address.setId(UUID.randomUUID());
        address.setUserId(userId);
        apply(address, request);
        address.setDefaultDelivery(count == 0);
        address.setDefaultBilling(count == 0);
        addresses.saveAndFlush(address);
        return list(userId);
    }

    @Transactional
    public List<AddressResponse> update(UUID userId, UUID id, AddressRequest request) {
        lock(userId);
        Address address = find(userId, id);
        apply(address, request);
        addresses.saveAndFlush(address);
        return list(userId);
    }

    /** The default delivery address stays until another one is made the default; a removed billing default moves to it. */
    @Transactional
    public List<AddressResponse> remove(UUID userId, UUID id) {
        lock(userId);
        Address address = find(userId, id);
        if (address.isDefaultDelivery()) {
            throw new AccessConflictException("address_is_default");
        }
        addresses.delete(address);
        addresses.flush();
        if (address.isDefaultBilling()) {
            addresses.findByUserIdOrderByCreatedAtAscIdAsc(userId).stream().filter(Address::isDefaultDelivery).findFirst()
                    .ifPresent(a -> a.setDefaultBilling(true));
        }
        return list(userId);
    }

    /** The old default is cleared and flushed before the new one is set, so the unique index never sees two. */
    @Transactional
    public List<AddressResponse> makeDefault(UUID userId, UUID id, String kind) {
        lock(userId);
        Address chosen = find(userId, id);
        boolean delivery = kind.equals("delivery");
        for (Address a : addresses.findByUserIdOrderByCreatedAtAscIdAsc(userId)) {
            if (delivery) {
                a.setDefaultDelivery(false);
            } else {
                a.setDefaultBilling(false);
            }
        }
        addresses.flush();
        if (delivery) {
            chosen.setDefaultDelivery(true);
        } else {
            chosen.setDefaultBilling(true);
        }
        addresses.flush();
        return list(userId);
    }

    private void apply(Address a, AddressRequest r) {
        Map<String, String> problems = CheckoutRules.addressProblems(r.country(), r.postcode(), r.city(), r.phone());
        if (!problems.isEmpty()) {
            throw new FieldValidationException(problems);
        }
        a.setLabel(r.label().strip());
        a.setFirstName(r.firstName().strip());
        a.setLastName(r.lastName().strip());
        a.setStreet(r.street().strip());
        a.setFlat(r.flat() == null || r.flat().isBlank() ? null : r.flat().strip());
        a.setPostcode(r.postcode().strip().toUpperCase(Locale.ROOT));
        a.setCity(r.city().strip());
        a.setCountry(r.country());
        a.setPhone(r.phone().strip());
    }

    private Address find(UUID userId, UUID id) {
        return addresses.findByIdAndUserId(id, userId).orElseThrow(AddressNotFoundException::new);
    }

    private void lock(UUID userId) {
        users.lockById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    static AddressResponse view(Address a) {
        return new AddressResponse(a.getId(), a.getLabel(), a.getFirstName(), a.getLastName(), a.getStreet(), a.getFlat(), a.getPostcode(),
                a.getCity(), a.getCountry(), a.getPhone(), a.isDefaultDelivery(), a.isDefaultBilling());
    }
}
