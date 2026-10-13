// Spring Data JPA repository for products; listing and search go through SearchService instead.
package com.iloveshopping.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    /** The staff list: brand and category in the same query, so a page of 50 is not 100 more lookups. */
    @Override
    @EntityGraph(attributePaths = {"brand", "category"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    Optional<Product> findByIdAndActiveTrue(UUID id);

    boolean existsByIdAndActiveTrue(UUID id);

    /** Active or not, with the brand in the same query, so a cart of n lines is not n brand lookups. */
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.brand WHERE p.id IN :ids")
    List<Product> findWithBrandByIdIn(Collection<UUID> ids);

    /** The colours and sizes of one product: every active product sharing its attributes.variant_group. */
    @Query(value = "SELECT * FROM products WHERE active AND attributes ->> 'variant_group' = :group ORDER BY price, name",
            nativeQuery = true)
    List<Product> findActiveVariants(String group);

    /**
     * Takes {@code quantity} off the shelf only if that many are left, as one statement. Two buyers of
     * the last one both send it; the row lock makes the second wait, Postgres re-checks its WHERE
     * against the first one's result, and it changes 0 rows. Returns the rows changed.
     */
    @Modifying
    @Query(value = """
            UPDATE products SET stock_quantity = stock_quantity - :quantity
            WHERE id = :id AND active AND stock_quantity >= :quantity""", nativeQuery = true)
    int reserveStock(UUID id, int quantity);

    @Modifying
    @Query(value = "UPDATE products SET stock_quantity = stock_quantity + :quantity WHERE id = :id", nativeQuery = true)
    int releaseStock(UUID id, int quantity);

    /** Case-insensitive, like uq_products_sku; {@code id} null for a product not saved yet. */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM products WHERE lower(sku) = lower(:sku) AND (CAST(:id AS uuid) IS NULL OR id <> :id))""",
            nativeQuery = true)
    boolean skuTakenByOther(String sku, UUID id);

    /** The products whose SKU is among {@code lowerSkus} (already lower-cased), archived ones too. */
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.brand WHERE lower(p.sku) IN :lowerSkus")
    List<Product> findBySkuLowerIn(Collection<String> lowerSkus);

    /** Rows of (category id, count) over the products not archived; a shelf with none has no row. */
    @Query("SELECT p.category.id, count(p) FROM Product p WHERE p.archivedAt IS NULL GROUP BY p.category.id")
    List<Object[]> countByCategory();

    /** Any product at all, archived ones too: their rows still point at the shelf. */
    boolean existsByCategoryId(Integer categoryId);

    /** Straight from the table, past any Product the persistence context already holds. */
    @Query(value = "SELECT stock_quantity FROM products WHERE id = :id", nativeQuery = true)
    Integer currentStock(UUID id);
}
