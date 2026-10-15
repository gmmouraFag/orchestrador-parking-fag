package br.edu.fag.parking;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class Startup {
    private final LogService logs;
    private final ParkingService parking;
    public Startup(LogService logs, ParkingService parking) { this.logs = logs; this.parking = parking; }
    @EventListener(ApplicationReadyEvent.class)
    public void ready() { parking.initialize(); logs.internal("INFO", "Orquestrador iniciado"); }
}
