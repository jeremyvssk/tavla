// Spring Data JPA repository for categories.
package com.iloveshopping.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    List<Category> findByActiveTrueOrderByNameAsc();

    boolean existsBySlug(String slug);
}
