// Spring Data JPA repository for products; listing and search go through SearchService instead.
package com.iloveshopping.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndActiveTrue(UUID id);

    boolean existsByIdAndActiveTrue(UUID id);

    /** The colours and sizes of one product: every active product sharing its attributes.variant_group. */
    @Query(value = "SELECT * FROM products WHERE active AND attributes ->> 'variant_group' = :group ORDER BY price, name",
            nativeQuery = true)
    List<Product> findActiveVariants(String group);
}
