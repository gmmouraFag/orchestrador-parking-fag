package br.edu.fag.parking;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class Startup {
    private final LogService logs;
    public Startup(LogService logs) { this.logs = logs; }
    @EventListener(ApplicationReadyEvent.class)
    public void ready() { logs.internal("INFO", "Orquestrador iniciado"); }
}
