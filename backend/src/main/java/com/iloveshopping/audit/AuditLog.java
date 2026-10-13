// The audit log: every staff change and sign-in written in the change's own transaction, and read back for admins.
package com.iloveshopping.audit;

import com.iloveshopping.audit.dto.AuditEntryResponse;
import com.iloveshopping.audit.dto.AuditPageResponse;
import com.iloveshopping.audit.dto.AuditParams;
import com.iloveshopping.auth.AuthPrincipal;
import com.iloveshopping.csv.Csv;
import com.iloveshopping.user.Role;
import com.iloveshopping.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@link #record} joins the caller's transaction, so the entry commits with the change it describes and
 * a rolled-back change leaves no entry behind. Who did it comes from the access token; where from, from
 * the request, cut to its first two parts. Nothing here updates or deletes an entry: the table's trigger
 * would refuse it anyway, and {@link AuditRetention} deletes only what is past the 12 months.
 */
@Service
public class AuditLog {

    private static final int EXPORT_LIMIT = 10_000;

    private final AuditRepository entries;
    private final UserService userService;
    private final ZoneId zone;

    public AuditLog(AuditRepository entries, UserService userService, @Value("${app.orders.time-zone}") String zone) {
        this.entries = entries;
        this.userService = userService;
        this.zone = ZoneId.of(zone);
    }

    /** By the signed-in staff member making the current request. */
    @Transactional
    public void record(AuditEvent event) {
        AuthPrincipal actor = currentPrincipal();
        entries.save(new AuditEntry(actor == null ? null : actor.userId(), actor == null ? null : actor.role(), event,
                maskIp(currentIp()), Instant.now()));
    }

    /** By someone not signed in yet: a sign-in, or a refused one ({@code actorId} null when it isn't known who). */
    @Transactional
    public void recordFor(UUID actorId, Role actorRole, AuditEvent event) {
        entries.save(new AuditEntry(actorId, actorRole, event, maskIp(currentIp()), Instant.now()));
    }

    @Transactional(readOnly = true)
    public AuditPageResponse page(AuditParams params) {
        int page = params.page() == null ? 0 : params.page();
        int size = params.size() == null ? 50 : params.size();
        Page<AuditEntry> found = entries.findAll(filter(params), PageRequest.of(page, size, newestFirst()));
        List<UUID> actorIds = entries.findActorIds();
        Map<UUID, String> names = userService.namesOf(union(actorIds, found.getContent()));
        List<AuditPageResponse.Actor> actors = actorIds.stream().filter(names::containsKey)
                .map(id -> new AuditPageResponse.Actor(id, names.get(id)))
                .sorted(Comparator.comparing(AuditPageResponse.Actor::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new AuditPageResponse(found.getContent().stream().map(e -> view(e, names)).toList(),
                page, size, found.getTotalElements(), found.getTotalPages(), actors);
    }

    /** The newest entries for the overview: everyone's for an admin, only their own for other staff. */
    @Transactional(readOnly = true)
    public List<AuditEntryResponse> recent(UUID onlyActor, int limit) {
        Specification<AuditEntry> spec = onlyActor == null ? Specification.unrestricted()
                : (root, query, cb) -> cb.equal(root.get("actorId"), onlyActor);
        List<AuditEntry> found = entries.findAll(spec, PageRequest.of(0, limit, newestFirst())).getContent();
        Map<UUID, String> names = userService.namesOf(union(List.of(), found));
        return found.stream().map(e -> view(e, names)).toList();
    }

    /** The filtered log as a CSV file, newest first, at most 10,000 rows. */
    @Transactional(readOnly = true)
    public byte[] export(AuditParams params) {
        List<AuditEntry> found = entries.findAll(filter(params), PageRequest.of(0, EXPORT_LIMIT, newestFirst())).getContent();
        Map<UUID, String> names = userService.namesOf(union(List.of(), found));
        DateTimeFormatter time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone);
        List<List<?>> rows = new ArrayList<>();
        for (AuditEntry e : found) {
            AuditEntryResponse v = view(e, names);
            String what = v.action().replace("{target}", v.target() == null ? "" : v.target());
            rows.add(List.of(time.format(e.getAt()), v.actor() == null ? "" : v.actor(),
                    v.actorRole() == null ? "" : v.actorRole().name(), what,
                    v.change() == null || v.change().from() == null ? "" : v.change().from(),
                    v.change() == null ? "" : v.change().to(), v.detail() == null ? "" : v.detail(),
                    v.flag() == null ? "" : v.flag().text(), v.ip() == null ? "" : v.ip()));
        }
        return Csv.file(List.of("When", "Who", "Role", "What", "Before", "After", "Detail", "Flag", "From"), rows);
    }

    private Specification<AuditEntry> filter(AuditParams params) {
        Instant since = switch (params.since() == null ? "today" : params.since()) {
            case "7d" -> LocalDate.now(zone).minusDays(6).atStartOfDay(zone).toInstant();
            case "30d" -> LocalDate.now(zone).minusDays(29).atStartOfDay(zone).toInstant();
            default -> LocalDate.now(zone).atStartOfDay(zone).toInstant();
        };
        Specification<AuditEntry> spec = (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("at"), since);
        if (params.actor() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorId"), params.actor()));
        }
        if (params.kind() != null) {
            AuditKind kind = AuditKind.valueOf(params.kind().toUpperCase(Locale.ROOT));
            spec = spec.and((root, query, cb) -> cb.equal(root.get("kind"), kind));
        }
        return spec;
    }

    private static Sort newestFirst() {
        return Sort.by(Sort.Order.desc("at"), Sort.Order.desc("id"));
    }

    private static Set<UUID> union(Collection<UUID> ids, List<AuditEntry> found) {
        Set<UUID> all = new HashSet<>(ids);
        for (AuditEntry e : found) {
            if (e.getActorId() != null) {
                all.add(e.getActorId());
            }
            if (e.getTargetType() == AuditTarget.USER && e.getTargetId() != null) {
                all.add(UUID.fromString(e.getTargetId()));
            }
        }
        return all;
    }

    private static AuditEntryResponse view(AuditEntry e, Map<UUID, String> names) {
        String target = e.getTargetType() == AuditTarget.USER && e.getTargetId() != null
                ? names.getOrDefault(UUID.fromString(e.getTargetId()), "a deleted account") : e.getTargetLabel();
        String link = e.getTargetType() == null || e.getTargetId() == null ? null : e.getTargetType().link(e.getTargetId());
        AuditEntryResponse.Change change = e.getChangeTo() == null ? null
                : new AuditEntryResponse.Change(e.getChangeFrom(), e.getChangeTo());
        AuditEntryResponse.Flag flag = e.getFlagText() == null ? null : new AuditEntryResponse.Flag(e.getFlagText(), e.isFlagSerious());
        return new AuditEntryResponse(e.getId().toString(), e.getAt(), e.getActorId() == null ? null : names.get(e.getActorId()),
                e.getActorRole(), e.getAction(), target, link, change, e.getDetail(), flag, e.getIp());
    }

    private static AuthPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthPrincipal p ? p : null;
    }

    private static String currentIp() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest().getRemoteAddr() : null;
    }

    /** "84.50.17.203" is stored as "84.50.•.•", an IPv6 address as its first two groups: enough to tell networks apart. */
    static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        if (ip.contains(":")) {
            String[] groups = ip.split(":", -1);
            return groups[0] + ":" + (groups.length > 1 ? groups[1] : "") + ":•";
        }
        String[] parts = ip.split("\\.");
        return parts.length == 4 ? parts[0] + "." + parts[1] + ".•.•" : "•";
    }
}
