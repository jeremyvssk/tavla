// Spring Data JPA repository for products; listing and search go through SearchService instead.
package com.iloveshopping.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndActiveTrue(UUID id);

    boolean existsByIdAndActiveTrue(UUID id);
}
