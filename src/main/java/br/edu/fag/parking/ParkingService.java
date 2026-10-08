package br.edu.fag.parking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class ParkingService {
    private final SpotRepository spots;
    private final ObjectMapper json;
    private final String source;
    private final boolean removeMissing;
    private final long staleSeconds;
    public ParkingService(SpotRepository spots, ObjectMapper json,
                          @Value("${parking.source}") String source,
                          @Value("${parking.remove-missing}") boolean removeMissing,
                          @Value("${parking.stale-seconds}") long staleSeconds) {
        this.spots = spots; this.json = json; this.source = source;
        this.removeMissing = removeMissing; this.staleSeconds = staleSeconds;
    }
    private void validateSource(String received) {
        if (!source.equals(received)) throw new IllegalArgumentException("Origem de monitoramento não configurada");
    }
    private void validateTime(Instant time) {
        if (time.isAfter(Instant.now().plusSeconds(30)))
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
            spots.deleteAll(existing.stream().filter(s -> !codes.contains(s.spotCode)).toList());
        return new Api.Applied(applied, ignored);
    }
    @Transactional
    public Api.Applied events(Api.Events request) {
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
        return new Api.Applied(applied, ignored);
    }
    private void update(Spot spot, Status status, Instant observed) {
        if (spot.status != status) { spot.status = status; spot.lastUpdated = observed; }
        spot.lastObserved = observed;
    }
    @Transactional(readOnly = true)
    public Api.Snapshot snapshot() {
        var rows = spots.findAllByOrderBySectorAscLayoutOrderAsc();
        var views = rows.stream().map(s -> new Api.SpotView(s.spotCode, s.status, s.lastUpdated,
                s.lastObserved, s.sector, s.accessible, s.layoutOrder, decode(s.polygonJson))).toList();
        long free = views.stream().filter(s -> s.status() == Status.FREE).count();
        long occupied = views.stream().filter(s -> s.status() == Status.OCCUPIED).count();
        long unknown = views.size() - free - occupied;
        Double percent = free + occupied == 0 ? null : Math.round(occupied * 1000.0 / (free + occupied)) / 10.0;
        var last = rows.stream().map(s -> s.lastObserved).max(Comparator.naturalOrder()).orElse(null);
        var now = Instant.now();
        // Retain the latest state during outages as requested; freshness is separate metadata.
        boolean active = !rows.isEmpty() && rows.stream().allMatch(s -> !s.lastObserved.isBefore(now.minusSeconds(staleSeconds)));
        return new Api.Snapshot(views, new Api.Summary(views.size(), free, occupied, unknown, percent), now, last, active);
    }
}
