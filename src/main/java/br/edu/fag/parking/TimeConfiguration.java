package br.edu.fag.parking;

import org.springframework.context.annotation.*;
import java.time.Clock;

@Configuration
public class TimeConfiguration {
    @Bean public Clock parkingClock() { return Clock.systemUTC(); }
}
