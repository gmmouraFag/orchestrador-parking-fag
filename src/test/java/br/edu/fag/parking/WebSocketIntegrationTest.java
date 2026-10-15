package br.edu.fag.parking;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import java.net.*;
import java.net.http.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:websocket;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class WebSocketIntegrationTest {
    @LocalServerPort int port;
    @Autowired ParkingService parking;
    @Autowired ObjectMapper json;
    static class Listener implements WebSocket.Listener {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final StringBuilder text = new StringBuilder();
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            text.append(data);
            if (last) { messages.add(text.toString()); text.setLength(0); }
            ws.request(1); return null;
        }
        JsonNode await(ObjectMapper json, String status) throws Exception {
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < until) {
                String message = messages.poll(1,TimeUnit.SECONDS);
                if (message == null) continue;
                assertThat(message).doesNotContain("secret-owner", "tokenHash", "reservationId");
                var value = json.readTree(message);
                if (value.path("spots").path(0).path("status").asText().equals(status)) return value;
            }
            throw new AssertionError("Evento não recebido: " + status);
        }
    }
    @Test void twoRealClientsReceiveCommittedReservationAndPythonOccupation() throws Exception {
        var points = List.of(new Api.Point(.1,.1),new Api.Point(.2,.1),new Api.Point(.2,.2));
        parking.sync(new Api.Sync("parking-video",Instant.now().minusSeconds(1),List.of(new Api.SpotInput("A",Status.FREE,"A",false,0,points))));
        var first = new Listener(); var second = new Listener();
        var http = HttpClient.newHttpClient();
        var uri = URI.create("ws://127.0.0.1:"+port+"/api/v1/events");
        var one = http.newWebSocketBuilder().buildAsync(uri,first).get(10,TimeUnit.SECONDS);
        var two = http.newWebSocketBuilder().buildAsync(uri,second).get(10,TimeUnit.SECONDS);
        try {
            first.await(json,"FREE"); second.await(json,"FREE");
            var reservation = parking.reserve("secret-owner","A").reservation();
            var yellow = first.await(json,"RESERVED");
            assertThat(second.await(json,"RESERVED").path("version").asLong()).isEqualTo(yellow.path("version").asLong());
            var observed = Instant.now();
            var payload = json.writeValueAsString(new Api.Events("parking-video",List.of(new Api.Event("A",Status.OCCUPIED,observed))));
            var response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/parking-spots/events"))
                .header("X-API-Key","test-key").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(payload)).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(first.await(json,"OCCUPIED").path("version").asLong()).isGreaterThan(yellow.path("version").asLong());
            second.await(json,"OCCUPIED");
            assertThat(parking.current("secret-owner").reservation().status()).isEqualTo(Reservation.State.PENDING_CONFIRMATION);
            parking.transition("secret-owner",reservation.id(),"confirm");
            assertThat(parking.current("secret-owner").reservation().status()).isEqualTo(Reservation.State.COMPLETED);
        } finally { one.abort(); two.abort(); }
    }
}
