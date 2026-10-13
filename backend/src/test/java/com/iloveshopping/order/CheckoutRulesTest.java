// Unit tests for the checkout's cross-field rules: postcode format per country, and the postcode belonging to the city.
package com.iloveshopping.order;

import com.iloveshopping.order.dto.PlaceOrderRequest;
import com.iloveshopping.order.exception.OrderValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutRulesTest {

    private static PlaceOrderRequest at(String country, String postcode, String city) {
        return new PlaceOrderRequest("mart.kask@example.com", "+372 5876 2210", "Mart", "Kask", "Pärnu mnt 22", null,
                postcode, city, country, "standard", null, null);
    }

    @ParameterizedTest
    @CsvSource({
            "EE, 10141, Tallinn", "EE, 15169, tallinn", "EE, 50090, Tartu", "EE, 80010, Pärnu", "EE, 80010, Parnu",
            "EE, 20308, Narva", "EE, 93815, Kuressaare",
            "LV, LV-1050, Rīga", "LV, 1050, Riga", "LV, LV-5401, Daugavpils",
            "LT, LT-01103, Vilnius", "LT, LT-44248, Kaunas", "LT, 91001, Klaipėda",
            "FI, 00100, Helsinki", "FI, 02100, Espoo", "FI, 33100, Tampere"})
    void aPostcodeThatFitsItsCityAndCountry_passes(String country, String postcode, String city) {
        assertThatCode(() -> CheckoutRules.check(at(country, postcode, city))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "EE, 50090, Tallinn, 'Postcodes in Tallinn start with 10 to 15, like 10141. Check the postcode, or the city.'",
            "EE, 10141, Tartu, 'Postcodes in Tartu start with 50 or 51, like 50090. Check the postcode, or the city.'",
            "LV, LV-3401, Riga, 'Postcodes in Rīga start with LV-10, like LV-1050. Check the postcode, or the city.'",
            "LT, LT-44248, Vilnius, 'Postcodes in Vilnius start with LT-01 to LT-14, like LT-01103. Check the postcode, or the city.'",
            "FI, 33100, '  HELSINKI ', 'Postcodes in Helsinki start with 00, like 00100. Check the postcode, or the city.'"})
    void aPostcodeFromAnotherCity_isCaught(String country, String postcode, String city, String message) {
        assertThatThrownBy(() -> CheckoutRules.check(at(country, postcode, city)))
                .isInstanceOfSatisfying(OrderValidationException.class,
                        e -> assertThat(e.getFields()).containsEntry("postcode", message));
    }

    @Test
    void aWrongFormat_isReportedAsTheFormat_notAlsoAsTheCity() {
        assertThatThrownBy(() -> CheckoutRules.check(at("EE", "1014", "Tallinn")))
                .isInstanceOfSatisfying(OrderValidationException.class,
                        e -> assertThat(e.getFields()).containsEntry("postcode", "Estonian postcodes have 5 digits, like 10141."));
    }

    @Test
    void aCityWithTheSameNameInAnotherCountry_isntChecked() {
        // the list is per country; a Tallinn in Latvia is just a Latvian address with a valid format
        assertThatCode(() -> CheckoutRules.check(at("LV", "LV-3401", "Tallinn"))).doesNotThrowAnyException();
    }

    @Test
    void aPhoneWithTooFewDigits_isRefused() {
        PlaceOrderRequest shortPhone = new PlaceOrderRequest("mart.kask@example.com", "+372 58 7", "Mart", "Kask",
                "Pärnu mnt 22", null, "10141", "Tallinn", "EE", "standard", null, null);
        assertThatThrownBy(() -> CheckoutRules.check(shortPhone))
                .isInstanceOfSatisfying(OrderValidationException.class, e -> assertThat(e.getFields()).containsKey("phone"));
    }
}
