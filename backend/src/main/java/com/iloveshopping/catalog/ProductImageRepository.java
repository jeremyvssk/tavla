// Spring Data JPA repository for product image records.
package com.iloveshopping.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, Integer> {

    List<ProductImage> findByProductIdOrderByPrimaryDescDisplayOrderAscIdAsc(UUID productId);

    Optional<ProductImage> findByIdAndProductId(Integer id, UUID productId);

    boolean existsByProductId(UUID productId);

    @Query("select coalesce(max(i.displayOrder), -1) + 1 from ProductImage i where i.productId = :productId")
    int nextDisplayOrder(@Param("productId") UUID productId);

    @Modifying
    @Query("update ProductImage i set i.primary = false where i.productId = :productId")
    void clearPrimary(@Param("productId") UUID productId);
}
