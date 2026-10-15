package br.edu.fag.parking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.concurrent.ConcurrentHashMap;

/** Public snapshots only. No session cookie, reservation ID or token is sent here. */
@Component
public class ParkingEvents extends TextWebSocketHandler {
    private final ParkingService parking;
    private final ObjectMapper json;
    private static class Client {
        final WebSocketSession session;
        long version = -1;
        Client(WebSocketSession session) { this.session = session; }
    }
    private final ConcurrentHashMap<String, Client> clients = new ConcurrentHashMap<>();
    public ParkingEvents(ParkingService parking, ObjectMapper json) { this.parking = parking; this.json = json; }
    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (clients.size() >= 100) { session.close(CloseStatus.SERVICE_OVERLOAD); return; }
        var client = new Client(session);
        clients.put(session.getId(), client);
        send(client, parking.snapshot());
    }
    @TransactionalEventListener
    public void committed(Api.Snapshot snapshot) { clients.values().forEach(client -> send(client, snapshot)); }
    private void send(Client client, Api.Snapshot snapshot) {
        synchronized (client) {
            if (snapshot.version() <= client.version) return;
            try {
                if (!client.session.isOpen()) { clients.remove(client.session.getId()); return; }
                client.session.sendMessage(new TextMessage(json.writeValueAsString(snapshot)));
                client.version = snapshot.version();
            } catch (Exception e) { drop(client); }
        }
    }
    private void drop(Client client) {
        clients.remove(client.session.getId());
        try { client.session.close(); } catch (Exception ignored) { /* already disconnected */ }
    }
    @Scheduled(fixedDelay = 20000)
    public void heartbeat() {
        clients.values().forEach(client -> {
            synchronized (client) {
                try { client.session.sendMessage(new PingMessage()); } catch (Exception e) { drop(client); }
            }
        });
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { clients.remove(session.getId()); }
    @Override public void handleTransportError(WebSocketSession session, Throwable error) {
        var client = clients.get(session.getId()); if (client != null) drop(client);
    }
}
