package br.edu.fag.parking;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ParkingController {
    private final ParkingService parking;
    private final LogService logs;
    public ParkingController(ParkingService parking, LogService logs) { this.parking = parking; this.logs = logs; }
    // One monitor, one JVM: serialize writes through transaction completion for deterministic ordering.
    @PostMapping("/parking-spots/sync")
    public synchronized Api.Applied sync(@Valid @RequestBody Api.Sync request) { return parking.sync(request); }
    @PostMapping("/parking-spots/events")
    public synchronized Api.Applied events(@Valid @RequestBody Api.Events request) { return parking.events(request); }
    @GetMapping("/parking-spots")
    public Api.Snapshot snapshot() { return parking.snapshot(); }
    @GetMapping("/parking-spots/summary")
    public Api.Summary summary() { return parking.snapshot().summary(); }
    @PostMapping("/logs")
    public synchronized Api.Applied logs(@Valid @RequestBody Api.Logs request) { return logs.receive(request); }
}
