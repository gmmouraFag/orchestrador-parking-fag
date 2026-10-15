package br.edu.fag.parking;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController @RequestMapping("/api/v1")
public class ReservationController {
    private static final String COOKIE = "parking_session";
    private final ParkingService parking;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private record Bucket(long minute, int count) {}
    public ReservationController(ParkingService parking) { this.parking = parking; }
    private String session(HttpServletRequest request, HttpServletResponse response, boolean create) {
        response.setHeader("Cache-Control", "no-store");
        if (request.getCookies() != null) for (var cookie : request.getCookies()) {
            if (COOKIE.equals(cookie.getName()) && cookie.getValue().matches("[A-Za-z0-9_-]{43}")) return cookie.getValue();
        }
        if (!create) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão ausente; recarregue a página");
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        response.addHeader("Set-Cookie", ResponseCookie.from(COOKIE, token).httpOnly(true)
            .secure(request.isSecure()).sameSite("Strict").path("/api/v1").maxAge(86400).build().toString());
        return token;
    }
    private void mutation(HttpServletRequest request) {
        // A non-simple header is required: cross-origin browsers must pass the configured CORS policy.
        if (!"mobile".equals(request.getHeader("X-Parking-Client")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cliente de reserva inválido");
        long minute = System.currentTimeMillis() / 60000;
        buckets.entrySet().removeIf(e -> e.getValue().minute() < minute - 1);
        if (buckets.size() > 10000) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Tente novamente mais tarde");
        var bucket = buckets.compute(request.getRemoteAddr(), (key, old) ->
            old == null || old.minute() != minute ? new Bucket(minute, 1) : new Bucket(minute, old.count() + 1));
        if (bucket.count() > 30) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Aguarde para tentar novamente");
    }
    @GetMapping("/reservations/current")
    public Api.PrivateSnapshot current(HttpServletRequest request, HttpServletResponse response) {
        return parking.current(session(request, response, true));
    }
    @PostMapping("/reservations") @ResponseStatus(HttpStatus.CREATED)
    public Api.PrivateSnapshot reserve(@Valid @RequestBody Api.Reserve input, HttpServletRequest request, HttpServletResponse response) {
        mutation(request); return parking.reserve(session(request, response, false), input.spotCode());
    }
    @PostMapping("/reservations/{id}/{action:confirm|reject|cancel}")
    public Api.PrivateSnapshot transition(@PathVariable String id, @PathVariable String action,
                                          HttpServletRequest request, HttpServletResponse response) {
        mutation(request); return parking.transition(session(request, response, false), id, action);
    }
    @GetMapping("/parking-spots/alternatives")
    public List<Api.SpotView> alternatives(@RequestParam String from) { return parking.alternatives(from); }
}
