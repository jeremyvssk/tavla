// Spring Data JPA repository for delivery options.
package com.iloveshopping.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryOptionRepository extends JpaRepository<DeliveryOption, String> {

    List<DeliveryOption> findAllByOrderByPositionAscNameAsc();
}
