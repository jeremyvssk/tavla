// What a delivery option costs: its price, or nothing past the free-shipping limit when it takes part; and new option ids.
package com.iloveshopping.delivery;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryOptionTest {

    @Test
    void anOptionThatTakesPartIsFreeFromExactlyTheLimit() {
        DeliveryOption standard = option("4.90", true);
        assertThat(standard.costFor(eur("100.00"), limit("100.00", true))).isEqualByComparingTo("0.00");
        assertThat(standard.costFor(eur("99.99"), limit("100.00", true))).isEqualByComparingTo("4.90");
    }

    @Test
    void anOptionThatDoesntTakePart_orALimitThatIsOff_costsThePrice() {
        assertThat(option("12.90", false).costFor(eur("150.00"), limit("100.00", true))).isEqualByComparingTo("12.90");
        assertThat(option("4.90", true).costFor(eur("150.00"), limit("100.00", false))).isEqualByComparingTo("4.90");
    }

    @Test
    void aFreeDeliveryHasTwoDecimalsLikeEveryOtherAmount() {
        assertThat(option("4.90", true).costFor(eur("150.00"), limit("100.00", true)).scale()).isEqualTo(2);
    }

    @Test
    void aNewOptionsIdComesFromItsName_andIsNeverOneAlreadyUsed() {
        assertThat(DeliveryService.newId("DPD Pickup point", Set.of())).isEqualTo("dpd-pickup-point");
        assertThat(DeliveryService.newId("Pakiautomaat (Omniva)", Set.of("pakiautomaat-omniva"))).isEqualTo("pakiautomaat-omniva-2");
        assertThat(DeliveryService.newId("Kuller Tänä", Set.of())).isEqualTo("kuller-tana");
        assertThat(DeliveryService.newId("€€€", Set.of())).isEqualTo("option");
    }

    private static DeliveryOption option(String price, boolean freeOverLimit) {
        DeliveryOption o = new DeliveryOption();
        o.setPrice(eur(price));
        o.setFreeOverLimit(freeOverLimit);
        return o;
    }

    private static ShopSettings limit(String from, boolean on) {
        ShopSettings s = new ShopSettings();
        s.setFreeShippingFrom(eur(from));
        s.setFreeShippingOn(on);
        return s;
    }

    private static BigDecimal eur(String value) {
        return new BigDecimal(value);
    }
}
