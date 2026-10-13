// The checkout checks that span two fields or count digits: the postcode for its country and city, a phone long enough to dial.
package com.iloveshopping.order;

import com.iloveshopping.order.dto.PlaceOrderRequest;
import com.iloveshopping.order.exception.OrderValidationException;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The same rules as the checkout page (frontend/src/validation/checkoutRules.ts), which is only the
 * friendly first pass: anyone can skip the browser and post to /orders.
 *
 * <p>Two postcode checks. The format, per country. Then, for the biggest cities, whether the postcode
 * belongs to the city: every Tallinn postcode starts with 10 to 15, so "Tallinn 50090" is a typo in one
 * of the two (50090 is Tartu). The list holds only cities whose postcodes all share known first digits,
 * so it can catch a mistake without refusing a real address; any other city gets the format check only.
 * Matching a street to its postcode would need a postal lookup service, which this project doesn't use.
 */
public final class CheckoutRules {

    private record Postcode(Pattern pattern, String message) {
    }

    private static final Map<String, Postcode> POSTCODES = Map.of(
            "EE", new Postcode(Pattern.compile("^\\d{5}$"), "Estonian postcodes have 5 digits, like 10141."),
            "LV", new Postcode(Pattern.compile("^(LV-?)?\\d{4}$", Pattern.CASE_INSENSITIVE), "Latvian postcodes have 4 digits, like LV-1050."),
            "LT", new Postcode(Pattern.compile("^(LT-?)?\\d{5}$", Pattern.CASE_INSENSITIVE), "Lithuanian postcodes have 5 digits, like LT-01103."),
            "FI", new Postcode(Pattern.compile("^\\d{5}$"), "Finnish postcodes have 5 digits, like 00100."));

    /** {@code digits} is matched against the postcode without its LV-/LT- prefix. */
    private record City(String country, String name, Pattern digits, String starts, String example) {
    }

    private static final List<City> CITIES = List.of(
            new City("EE", "Tallinn", Pattern.compile("^1[0-5]\\d{3}$"), "10 to 15", "10141"),
            new City("EE", "Tartu", Pattern.compile("^5[01]\\d{3}$"), "50 or 51", "50090"),
            new City("EE", "Narva", Pattern.compile("^2[01]\\d{3}$"), "20 or 21", "20308"),
            new City("EE", "Pärnu", Pattern.compile("^80\\d{3}$"), "80", "80010"),
            new City("LV", "Rīga", Pattern.compile("^10\\d{2}$"), "LV-10", "LV-1050"),
            new City("LV", "Daugavpils", Pattern.compile("^54\\d{2}$"), "LV-54", "LV-5401"),
            new City("LV", "Liepāja", Pattern.compile("^34\\d{2}$"), "LV-34", "LV-3401"),
            new City("LV", "Jelgava", Pattern.compile("^30\\d{2}$"), "LV-30", "LV-3001"),
            new City("LT", "Vilnius", Pattern.compile("^(0[1-9]|1[0-4])\\d{3}$"), "LT-01 to LT-14", "LT-01103"),
            new City("LT", "Kaunas", Pattern.compile("^(4[4-9]|5[0-2])\\d{3}$"), "LT-44 to LT-52", "LT-44248"),
            new City("FI", "Helsinki", Pattern.compile("^00\\d{3}$"), "00", "00100"),
            new City("FI", "Espoo", Pattern.compile("^02\\d{3}$"), "02", "02100"),
            new City("FI", "Vantaa", Pattern.compile("^01\\d{3}$"), "01", "01300"),
            new City("FI", "Tampere", Pattern.compile("^33\\d{3}$"), "33", "33100"),
            new City("FI", "Turku", Pattern.compile("^20\\d{3}$"), "20", "20100"),
            new City("FI", "Oulu", Pattern.compile("^90\\d{3}$"), "90", "90100"));

    private CheckoutRules() {
    }

    static void check(PlaceOrderRequest request) {
        Map<String, String> problems = addressProblems(request.country(), request.postcode(), request.city(), request.phone());
        PlaceOrderRequest.BillingAddress bill = request.billingAddress();
        if (bill != null) {
            addressProblems(bill.country(), bill.postcode(), bill.city(), null)
                    .forEach((field, problem) -> problems.put("billingAddress." + field, problem));
        }
        if (!problems.isEmpty()) {
            throw new OrderValidationException(problems);
        }
    }

    /**
     * The same checks for an address saved on the account (user/AddressService), so a saved address always
     * passes at checkout. Keys are the field names: postcode, phone. Empty when all is well.
     */
    public static Map<String, String> addressProblems(String country, String postcode, String city, String phone) {
        Map<String, String> problems = new LinkedHashMap<>();
        Postcode rule = POSTCODES.get(country);
        if (rule != null && !rule.pattern().matcher(postcode.strip()).matches()) {
            problems.put("postcode", rule.message());
        } else {
            String cityProblem = cityMismatch(country, city, postcode);
            if (cityProblem != null) {
                problems.put("postcode", cityProblem);
            }
        }
        if (phone != null && phone.replaceAll("\\D", "").length() < 8) {
            problems.put("phone", "Enter the full number with the country code, like +372 5123 4567.");
        }
        return problems;
    }

    /** A message when the city is one of {@link #CITIES} and the postcode can't be in it; else null. */
    static String cityMismatch(String country, String city, String postcode) {
        String key = fold(city);
        String digits = postcode.strip().toUpperCase(Locale.ROOT).replaceFirst("^(LV|LT)-?", "");
        return CITIES.stream()
                .filter(c -> c.country().equals(country) && fold(c.name()).equals(key))
                .filter(c -> !c.digits().matcher(digits).matches())
                .map(c -> "Postcodes in " + c.name() + " start with " + c.starts() + ", like " + c.example()
                        + ". Check the postcode, or the city.")
                .findFirst().orElse(null);
    }

    /** "  RĪGA " and "riga" are the same city: lower case, no accents, no spaces around. */
    private static String fold(String value) {
        return Normalizer.normalize(value.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
