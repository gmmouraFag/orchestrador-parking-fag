package br.edu.fag.parking;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration @EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {
    private final ParkingEvents events;
    public WebSocketConfiguration(ParkingEvents events) { this.events = events; }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // The stream contains only information also available from the public GET endpoint.
        registry.addHandler(events, "/api/v1/events").setAllowedOrigins("*");
    }
}
