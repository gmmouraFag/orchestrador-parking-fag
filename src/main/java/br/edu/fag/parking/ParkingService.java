package br.edu.fag.parking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class ParkingService {
    private final SpotRepository spots;
    private final ObjectMapper json;
    private final String source;
    private final boolean removeMissing;
    private final long staleSeconds;
    private final ReservationRepository reservations;
    private final StateRepository states;
    private final Clock clock;
    private final LogService logs;
    private final ApplicationEventPublisher publisher;
    public ParkingService(SpotRepository spots, ObjectMapper json,
                          @Value("${parking.source}") String source,
                          @Value("${parking.remove-missing}") boolean removeMissing,
                          @Value("${parking.stale-seconds}") long staleSeconds,
                          ReservationRepository reservations, StateRepository states, Clock clock,
                          LogService logs, ApplicationEventPublisher publisher) {
        this.spots = spots; this.json = json; this.source = source;
        this.removeMissing = removeMissing; this.staleSeconds = staleSeconds;
        this.reservations = reservations; this.states = states; this.clock = clock;
        this.logs = logs; this.publisher = publisher;
    }
    @Transactional
    public void initialize() { if (!states.existsById(1L)) states.saveAndFlush(new ParkingState()); }
    private Instant now() { return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    private ParkingState lock() { return Objects.requireNonNull(states.lock(1L), "Estado não inicializado"); }
    private void validateSource(String received) {
        if (!source.equals(received)) throw new IllegalArgumentException("Origem de monitoramento não configurada");
    }
    private void validateTime(Instant time) {
        if (time.isAfter(now().plusSeconds(30)))
            throw new IllegalArgumentException("Observação excessivamente no futuro");
    }
    private String encode(List<Api.Point> points) {
        try { return json.writeValueAsString(points); }
        catch (Exception e) { throw new IllegalArgumentException("Polígono inválido", e); }
    }
    private List<Api.Point> decode(String polygon) {
        try { return json.readValue(polygon, new TypeReference<>() {}); }
        catch (Exception e) { throw new IllegalStateException("Configuração persistida inválida", e); }
    }
    @Transactional
    public Api.Applied sync(Api.Sync request) {
        var state = lock();
        expire();
        request = new Api.Sync(request.source(), request.observedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS), request.spots());
        validateSource(request.source()); validateTime(request.observedAt());
        Set<String> codes = new HashSet<>();
        for (var input : request.spots()) {
            if (!codes.add(input.spotCode())) throw new IllegalArgumentException("Código de vaga duplicado");
            if (input.polygon().stream().anyMatch(p -> !Double.isFinite(p.x()) || !Double.isFinite(p.y())))
                throw new IllegalArgumentException("Coordenadas inválidas");
            double area = 0;
            for (int i = 0; i < input.polygon().size(); i++) {
                var a = input.polygon().get(i);
                var b = input.polygon().get((i + 1) % input.polygon().size());
                area += a.x() * b.y() - b.x() * a.y();
            }
            if (Math.abs(area) < 1e-10) throw new IllegalArgumentException("Polígono sem área");
        }
        var existing = spots.findAll();
        var newest = existing.stream().map(s -> s.lastObserved).max(Comparator.naturalOrder()).orElse(null);
        // Old snapshots cannot recreate deleted spots or roll back the configured catalogue.
        if (newest != null && request.observedAt().isBefore(newest))
            return new Api.Applied(0, request.spots().size());
        int applied = 0, ignored = 0;
        for (var input : request.spots()) {
            var current = existing.stream().filter(s -> s.spotCode.equals(input.spotCode())).findFirst().orElse(null);
            if (current == null) {
                current = new Spot(); current.spotCode = input.spotCode();
                current.status = input.status(); current.lastUpdated = request.observedAt();
                current.lastObserved = request.observedAt();
            } else if (!request.observedAt().isAfter(current.lastObserved)) {
                ignored++; continue;
            } else { update(current, input.status(), request.observedAt()); }
            current.sector = input.sector(); current.accessible = input.accessible();
            current.layoutOrder = input.layoutOrder(); current.polygonJson = encode(input.polygon());
            spots.save(current); applied++;
        }
        if (removeMissing && (newest == null || request.observedAt().isAfter(newest)))
            spots.deleteAll(existing.stream().filter(s -> !codes.contains(s.spotCode)
                && reservations.findAll().stream().noneMatch(r -> r.spotId.equals(s.id))).toList());
        changed(state);
        return new Api.Applied(applied, ignored);
    }
    @Transactional
    public Api.Applied events(Api.Events request) {
        var state = lock();
        expire();
        validateSource(request.source());
        for (var event : request.events()) {
            validateTime(event.observedAt());
            if (spots.findBySpotCode(event.spotCode()).isEmpty())
                throw new IllegalArgumentException("Vaga não cadastrada; envie sincronização completa");
        }
        int applied = 0, ignored = 0;
        for (var event : request.events()) {
            var current = spots.findBySpotCode(event.spotCode()).orElseThrow();
            var observed = event.observedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            if (!observed.isAfter(current.lastObserved)) { ignored++; continue; }
            update(current, event.status(), observed); applied++;
        }
        if (applied > 0) changed(state);
        return new Api.Applied(applied, ignored);
    }
    private void update(Spot spot, Status status, Instant observed) {
        if (spot.status != status) { spot.status = status; spot.lastUpdated = observed; }
        spot.lastObserved = observed;
        for (var r : reservations.findByActiveSpotIdIsNotNull()) {
            if (!r.spotId.equals(spot.id)) continue;
            // Delayed observations before the reservation cannot ask its owner to confirm.
            if (status == Status.OCCUPIED && !observed.isBefore(r.createdAt) && observed.isBefore(r.expiresAt)) {
                r.status = Reservation.State.PENDING_CONFIRMATION;
            } else if (status != Status.OCCUPIED && r.status == Reservation.State.PENDING_CONFIRMATION) {
                r.status = Reservation.State.ACTIVE;
            }
        }
    }
    @Transactional
    public Api.Snapshot snapshot() {
        var state = lock();
        boolean expired = expire();
        var snapshot = build(state);
        if (expired || state.monitoringActive != snapshot.monitoringActive()) {
            changed(state);
            return build(state);
        }
        return snapshot;
    }
    private boolean fresh(Spot s) { return !s.lastObserved.isBefore(now().minusSeconds(staleSeconds)); }
    private Api.Snapshot build(ParkingState state) {
        var rows = spots.findAllByOrderBySectorAscLayoutOrderAsc();
        var activeIds = reservations.findByActiveSpotIdIsNotNull().stream()
            .filter(r -> r.expiresAt.isAfter(now())).map(r -> r.spotId).collect(java.util.stream.Collectors.toSet());
        var views = rows.stream().map(s -> new Api.SpotView(s.spotCode,
                !fresh(s) ? "UNKNOWN" : s.status == Status.FREE && activeIds.contains(s.id) ? "RESERVED" : s.status.name(),
                s.status, s.lastUpdated,
                s.lastObserved, s.sector, s.accessible, s.layoutOrder, decode(s.polygonJson))).toList();
        long free = views.stream().filter(s -> s.status().equals("FREE")).count();
        long occupied = views.stream().filter(s -> s.status().equals("OCCUPIED")).count();
        long reserved = views.stream().filter(s -> s.status().equals("RESERVED")).count();
        long unknown = views.size() - free - occupied - reserved;
        Double percent = free + occupied + reserved == 0 ? null : Math.round(occupied * 1000.0 / (free + occupied + reserved)) / 10.0;
        var last = rows.stream().map(s -> s.lastObserved).max(Comparator.naturalOrder()).orElse(null);
        var now = now();
        // Retain the latest state during outages as requested; freshness is separate metadata.
        boolean active = !rows.isEmpty() && rows.stream().allMatch(s -> !s.lastObserved.isBefore(now.minusSeconds(staleSeconds)));
        return new Api.Snapshot(views, new Api.Summary(views.size(), free, occupied, reserved, unknown, percent), now, last, active, state.version);
    }

    private void changed(ParkingState state) {
        state.version++;
        var snapshot = build(state);
        state.monitoringActive = snapshot.monitoringActive();
        publisher.publishEvent(snapshot);
    }
    private boolean expire() {
        boolean changed = false;
        for (var r : reservations.findByActiveSpotIdIsNotNull()) {
            if (!r.expiresAt.isAfter(now())) {
                r.end(Reservation.State.EXPIRED);
                logs.internal("INFO", "Reserva expirada: " + r.id);
                changed = true;
            }
        }
        reservations.flush(); // Release unique active slots before creating a successor.
        return changed;
    }
    @Scheduled(fixedDelayString = "${RESERVATION_MAINTENANCE_MS:1000}")
    @Transactional
    public void maintenance() {
        var state = states.lock(1L);
        if (state == null) return;
        boolean expired = expire();
        if (expired || state.monitoringActive != build(state).monitoringActive()) changed(state);
    }
    static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private Api.ReservationView view(Reservation r) {
        return new Api.ReservationView(r.id, r.spotCode, r.status, r.createdAt, r.expiresAt, r.confirmedAt);
    }
    @Transactional
    public Api.PrivateSnapshot current(String token) {
        var state = lock();
        if (expire()) changed(state);
        var rows = reservations.findByTokenHashOrderByCreatedAtDesc(hash(token));
        var latest = rows.stream().filter(Reservation::live).findFirst().orElse(rows.isEmpty() ? null : rows.get(0));
        return new Api.PrivateSnapshot(latest == null ? null : view(latest), now());
    }
    @Transactional
    public Api.PrivateSnapshot reserve(String token, String code) {
        var state = lock();
        expire();
        var hash = hash(token);
        var own = reservations.findByTokenHashOrderByCreatedAtDesc(hash).stream().filter(Reservation::live).findFirst();
        if (own.isPresent()) {
            if (own.get().spotCode.equals(code)) return new Api.PrivateSnapshot(view(own.get()), now());
            throw conflict("Você já possui uma reserva ativa");
        }
        var spot = spots.findBySpotCode(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vaga inexistente"));
        if (!available(spot)) throw conflict("Vaga indisponível para reserva");
        var r = new Reservation();
        r.id = UUID.randomUUID().toString(); r.spotId = spot.id; r.spotCode = spot.spotCode;
        r.tokenHash = hash; r.status = Reservation.State.ACTIVE; r.createdAt = now();
        r.expiresAt = r.createdAt.plusSeconds(90); r.activeSpotId = spot.id; r.activeSessionHash = hash;
        reservations.saveAndFlush(r);
        logs.internal("INFO", "Reserva criada: " + r.id + " vaga " + code);
        changed(state);
        return new Api.PrivateSnapshot(view(r), now());
    }
    private boolean available(Spot s) {
        return !s.accessible && fresh(s) && s.status == Status.FREE
            && reservations.findByActiveSpotIdIsNotNull().stream().noneMatch(r -> r.spotId.equals(s.id) && r.expiresAt.isAfter(now()));
    }
    @Transactional
    public Api.PrivateSnapshot transition(String token, String id, String action) {
        var state = lock();
        expire();
        var r = reservations.findById(id).filter(v -> v.tokenHash.equals(hash(token)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva inexistente"));
        var target = switch (action) {
            case "confirm" -> Reservation.State.COMPLETED;
            case "reject" -> Reservation.State.DISPLACED;
            case "cancel" -> Reservation.State.CANCELLED;
            default -> throw new IllegalArgumentException("Operação inválida");
        };
        if (r.status == target) return new Api.PrivateSnapshot(view(r), now());
        if (!r.live()) throw conflict("Reserva encerrada");
        var spot = spots.findById(r.spotId).orElseThrow();
        if (!action.equals("cancel") && (r.status != Reservation.State.PENDING_CONFIRMATION || spot.status != Status.OCCUPIED || !fresh(spot)))
            throw conflict("Não há ocupação pendente de confirmação");
        r.end(target);
        if (target == Reservation.State.COMPLETED) r.confirmedAt = now();
        logs.internal("INFO", "Reserva " + target + ": " + r.id);
        changed(state);
        return new Api.PrivateSnapshot(view(r), now());
    }
    @Transactional
    public List<Api.SpotView> alternatives(String from) {
        var state = lock();
        if (expire()) changed(state);
        var origin = spots.findBySpotCode(from).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vaga inexistente"));
        var center = center(origin);
        var codes = spots.findAll().stream().filter(this::available).filter(s -> !s.id.equals(origin.id))
            .sorted(Comparator.comparingDouble((Spot s) -> {
                var p = center(s); return Math.hypot((p.x() - center.x()) * 1920, (p.y() - center.y()) * 1080);
            }).thenComparing(s -> s.spotCode)).map(s -> s.spotCode).toList();
        var snapshot = build(state);
        return codes.stream().map(code -> snapshot.spots().stream().filter(s -> s.spotCode().equals(code)).findFirst().orElseThrow()).toList();
    }
    private Api.Point center(Spot s) {
        var points = decode(s.polygonJson);
        return new Api.Point(points.stream().mapToDouble(Api.Point::x).average().orElseThrow(),
                             points.stream().mapToDouble(Api.Point::y).average().orElseThrow());
    }
}
