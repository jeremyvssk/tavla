// Spring Data JPA repository for brands.
package com.iloveshopping.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrandRepository extends JpaRepository<Brand, Integer> {

    List<Brand> findAllByOrderByNameAsc();

    boolean existsBySlug(String slug);
}
