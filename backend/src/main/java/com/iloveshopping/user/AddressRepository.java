// Spring Data JPA repository for saved addresses; always read through the owner's id.
package com.iloveshopping.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findByUserIdOrderByCreatedAtAscIdAsc(UUID userId);

    /** By owner and id together: an id from another account answers "not found", never someone else's address. */
    Optional<Address> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
