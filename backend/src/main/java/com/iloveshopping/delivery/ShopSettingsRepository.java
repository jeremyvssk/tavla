// Spring Data JPA repository for the shop's one settings row.
package com.iloveshopping.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopSettingsRepository extends JpaRepository<ShopSettings, Short> {
}
