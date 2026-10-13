// Delivery options and the free-shipping limit: staff edit them, the checkout lists them, and an order is priced by them.
package com.iloveshopping.delivery;

import com.iloveshopping.audit.AuditEvent;
import com.iloveshopping.audit.AuditKind;
import com.iloveshopping.audit.AuditLog;
import com.iloveshopping.audit.AuditTarget;
import com.iloveshopping.delivery.dto.DeliveryOptionRequest;
import com.iloveshopping.delivery.dto.DeliveryOptionResponse;
import com.iloveshopping.delivery.dto.DeliverySettingsResponse;
import com.iloveshopping.delivery.dto.FreeShippingRequest;
import com.iloveshopping.delivery.exception.DeliveryNotFoundException;
import com.iloveshopping.exception.FieldValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The checkout and the order use the same {@link #quote}, so the price a shopper is shown is worked out by
 * the same code as the price they are charged. An option is never deleted, only switched off: past orders
 * point at it (orders.shipping_method references delivery_options.id).
 */
@Service
public class DeliveryService {

    private final DeliveryOptionRepository options;
    private final ShopSettingsRepository settings;
    private final AuditLog auditLog;

    public DeliveryService(DeliveryOptionRepository options, ShopSettingsRepository settings, AuditLog auditLog) {
        this.options = options;
        this.settings = settings;
        this.auditLog = auditLog;
    }

    /** The staff's view: every option, switched off ones too. */
    @Transactional(readOnly = true)
    public DeliverySettingsResponse staffView() {
        return view(options.findAllByOrderByPositionAscNameAsc());
    }

    /** The checkout's view: the options shoppers can choose. */
    @Transactional(readOnly = true)
    public DeliverySettingsResponse shopView() {
        return view(options.findAllByOrderByPositionAscNameAsc().stream().filter(DeliveryOption::isActive).toList());
    }

    /**
     * What delivering an order of {@code subtotal} to {@code country} with option {@code optionId} costs.
     * Empty when the option doesn't exist, is switched off, or doesn't go to that country; the order is
     * then refused, the same as an unknown option.
     */
    @Transactional(readOnly = true)
    public Optional<DeliveryQuote> quote(String optionId, String country, BigDecimal subtotal) {
        ShopSettings shop = shop();
        return options.findById(optionId).filter(o -> o.isActive() && o.serves(country))
                .map(o -> new DeliveryQuote(o.getId(), o.getName(), o.costFor(subtotal, shop), o.getDaysMin(), o.getDaysMax()));
    }

    @Transactional
    public DeliverySettingsResponse saveFreeShipping(FreeShippingRequest request) {
        ShopSettings shop = shop();
        String before = freeText(shop.getFreeShippingFrom(), shop.isFreeShippingOn());
        shop.setFreeShippingFrom(request.from().setScale(2));
        shop.setFreeShippingOn(request.on());
        settings.save(shop);
        String after = freeText(shop.getFreeShippingFrom(), shop.isFreeShippingOn());
        if (!before.equals(after)) {
            auditLog.record(AuditEvent.of(AuditKind.PRODUCTS, "Changed {target}").target(AuditTarget.DELIVERY, "free-shipping",
                    "the free-shipping limit").change(before, after));
        }
        return staffView();
    }

    @Transactional
    public DeliverySettingsResponse create(DeliveryOptionRequest request) {
        List<DeliveryOption> all = options.findAllByOrderByPositionAscNameAsc();
        DeliveryOption option = new DeliveryOption();
        option.setId(newId(request.name(), all.stream().map(DeliveryOption::getId).collect(Collectors.toSet())));
        option.setPosition(all.stream().mapToInt(DeliveryOption::getPosition).max().orElse(-1) + 1);
        apply(option, request);
        options.save(option);
        auditLog.record(AuditEvent.of(AuditKind.PRODUCTS, "Added the delivery option {target}")
                .target(AuditTarget.DELIVERY, option.getId(), option.getName()).detail(summary(option)));
        return staffView();
    }

    @Transactional
    public DeliverySettingsResponse update(String id, DeliveryOptionRequest request) {
        DeliveryOption option = options.findById(id).orElseThrow(DeliveryNotFoundException::new);
        String before = summary(option);
        BigDecimal priceBefore = option.getPrice();
        apply(option, request);
        options.save(option);
        String after = summary(option);
        if (!before.equals(after)) {
            AuditEvent event = AuditEvent.of(AuditKind.PRODUCTS, "Edited the delivery option {target}")
                    .target(AuditTarget.DELIVERY, id, option.getName());
            auditLog.record(priceBefore.compareTo(option.getPrice()) != 0
                    ? event.change(euros(priceBefore), euros(option.getPrice())).detail(after)
                    : event.detail(after));
        }
        return staffView();
    }

    /** {@code ids} must name every option exactly once; the list is the order shoppers see them in. */
    @Transactional
    public DeliverySettingsResponse reorder(List<String> ids) {
        List<DeliveryOption> all = options.findAllByOrderByPositionAscNameAsc();
        Map<String, DeliveryOption> byId = all.stream().collect(Collectors.toMap(DeliveryOption::getId, Function.identity()));
        if (ids.size() != all.size() || !byId.keySet().equals(new HashSet<>(ids))) {
            throw new FieldValidationException("ids", "must list every delivery option once");
        }
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).setPosition(i);
        }
        options.saveAll(all);
        auditLog.record(AuditEvent.of(AuditKind.PRODUCTS, "Reordered {target}").target(AuditTarget.DELIVERY, "order",
                "the delivery options").detail(ids.stream().map(id -> byId.get(id).getName()).collect(Collectors.joining(", "))));
        return staffView();
    }

    private static void apply(DeliveryOption option, DeliveryOptionRequest request) {
        if (request.daysMax() < request.daysMin()) {
            throw new FieldValidationException("daysMax", "can't be fewer than the earliest day");
        }
        option.setName(request.name().strip());
        option.setPrice(request.price().setScale(2));
        option.setOrderBy(request.orderBy());
        option.setDaysMin(request.daysMin().shortValue());
        option.setDaysMax(request.daysMax().shortValue());
        option.setCountries(request.countries().stream().distinct().toArray(String[]::new));
        option.setActive(request.active());
        option.setFreeOverLimit(request.freeOverLimit());
    }

    /** "dpd-pickup" from "DPD pickup"; a number on the end when that is taken. Never reused: orders keep it. */
    static String newId(String name, Set<String> taken) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (base.isEmpty()) {
            base = "option";
        }
        base = base.length() > 32 ? base.substring(0, 32).replaceAll("-+$", "") : base;
        String id = base;
        for (int n = 2; taken.contains(id); n++) {
            id = base + "-" + n;
        }
        return id;
    }

    private ShopSettings shop() {
        return settings.findById(ShopSettings.ID).orElseThrow(() -> new IllegalStateException("shop_settings has no row"));
    }

    private DeliverySettingsResponse view(List<DeliveryOption> list) {
        ShopSettings shop = shop();
        return new DeliverySettingsResponse(new DeliverySettingsResponse.FreeShipping(shop.getFreeShippingFrom(), shop.isFreeShippingOn()),
                list.stream().map(DeliveryService::toResponse).toList());
    }

    private static DeliveryOptionResponse toResponse(DeliveryOption o) {
        return new DeliveryOptionResponse(o.getId(), o.getName(), o.getPrice(), o.getOrderBy(), o.getDaysMin(),
                o.getDaysMax(), Arrays.asList(o.getCountries()), o.isActive(), o.isFreeOverLimit());
    }

    private static String summary(DeliveryOption o) {
        return o.getName() + ", " + euros(o.getPrice()) + ", " + o.getDaysMin() + "–" + o.getDaysMax() + " days, by "
                + o.getOrderBy() + ", " + String.join(" ", o.getCountries())
                + (o.isFreeOverLimit() ? ", free over the limit" : "") + (o.isActive() ? "" : ", switched off");
    }

    private static String freeText(BigDecimal from, boolean on) {
        return on ? "Free from " + euros(from) : "Off";
    }

    private static String euros(BigDecimal amount) {
        return "€" + amount.setScale(2).toPlainString();
    }
}
