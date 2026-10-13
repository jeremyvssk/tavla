// Spring Data JPA repository for the team's order notes.
package com.iloveshopping.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderNoteRepository extends JpaRepository<OrderNote, Long> {

    List<OrderNote> findByOrderIdOrderByIdAsc(UUID orderId);
}
