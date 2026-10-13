// Spring Data JPA repository for categories.
package com.iloveshopping.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    List<Category> findByActiveTrueOrderByPositionAscNameAsc();

    List<Category> findAllByOrderByPositionAscNameAsc();

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Integer id);

    boolean existsByParentId(Integer parentId);

    @Query("SELECT coalesce(max(c.position), -1) + 1 FROM Category c WHERE c.parentId IS NOT DISTINCT FROM :parentId")
    int nextPosition(Integer parentId);
}
