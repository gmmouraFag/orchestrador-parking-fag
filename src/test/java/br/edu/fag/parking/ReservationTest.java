package br.edu.fag.parking;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:reservations;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc @ActiveProfiles("test")
class ReservationTest {
    static class MutableClock extends Clock {
        volatile Instant time = Instant.parse("2026-10-15T15:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return time; }
    }
    @TestConfiguration static class Config { @Bean @Primary MutableClock testClock() { return new MutableClock(); } }
    @Autowired MutableClock clock;
    @Autowired ParkingService parking;
    @Autowired ReservationRepository reservations;
    @Autowired SpotRepository spots;
    @Autowired MockMvc mvc;
    Api.SpotInput spot(String code, boolean pcd, double x) {
        return new Api.SpotInput(code, Status.FREE, "A", pcd, 0,
            List.of(new Api.Point(x,.1), new Api.Point(x+.01,.1), new Api.Point(x+.01,.2)));
    }
    void sync() { parking.sync(new Api.Sync("parking-video", clock.instant(), List.of(spot("A",false,.1), spot("Z",false,.12), spot("B",false,.8), spot("PCD",true,.11)))); }
    @BeforeEach void setup() { reservations.deleteAll(); spots.deleteAll(); clock.time = Instant.parse("2026-10-15T15:00:00Z"); sync(); }
    void occupy(String code) { clock.time = clock.time.plusSeconds(1); parking.events(new Api.Events("parking-video", List.of(new Api.Event(code,Status.OCCUPIED,clock.instant())))); }
    @Test void durationAndExpiryUseServerClockAndReleaseUniqueSlot() {
        var r = parking.reserve("one", "A").reservation();
        assertThat(Duration.between(r.createdAt(),r.expiresAt()).toSeconds()).isEqualTo(90);
        assertThat(parking.snapshot().spots().get(0).status()).isEqualTo("RESERVED");
        clock.time = r.expiresAt().minusNanos(1000);
        assertThat(parking.current("one").reservation().status()).isEqualTo(Reservation.State.ACTIVE);
        clock.time = r.expiresAt();
        assertThat(parking.current("one").reservation().status()).isEqualTo(Reservation.State.EXPIRED);
        sync();
        assertThat(parking.reserve("two", "A").reservation().id()).isNotEqualTo(r.id());
    }
    @Test void confirmRequiresOwnerAndOccupationAndIsIdempotent() {
        var r = parking.reserve("one", "A").reservation();
        assertThatThrownBy(() -> parking.transition("one",r.id(),"confirm")).hasMessageContaining("409");
        occupy("A");
        assertThat(parking.current("one").reservation().status()).isEqualTo(Reservation.State.PENDING_CONFIRMATION);
        assertThatThrownBy(() -> parking.transition("other",r.id(),"confirm")).hasMessageContaining("404");
        assertThat(parking.transition("one",r.id(),"confirm").reservation().status()).isEqualTo(Reservation.State.COMPLETED);
        assertThat(parking.transition("one",r.id(),"confirm").reservation().status()).isEqualTo(Reservation.State.COMPLETED);
        assertThat(spots.findBySpotCode("A").orElseThrow().status).isEqualTo(Status.OCCUPIED);
    }
    @Test void rejectSuggestsSpatialAlternativeWithoutReservingIt() {
        var r = parking.reserve("one", "A").reservation(); occupy("A");
        parking.transition("one",r.id(),"reject");
        assertThat(parking.alternatives("A")).extracting(Api.SpotView::spotCode).containsExactly("Z","B");
        assertThat(reservations.findByActiveSpotIdIsNotNull()).isEmpty();
        assertThat(parking.reserve("one","Z").reservation().status()).isEqualTo(Reservation.State.ACTIVE);
    }
    @Test void releaseWithdrawsPopupAndNoResponseNeverConfirms() {
        var r = parking.reserve("one", "A").reservation(); occupy("A");
        clock.time = clock.time.plusSeconds(1);
        parking.events(new Api.Events("parking-video", List.of(new Api.Event("A",Status.FREE,clock.instant()))));
        assertThat(parking.current("one").reservation().status()).isEqualTo(Reservation.State.ACTIVE);
        occupy("A"); clock.time = r.expiresAt();
        assertThat(parking.current("one").reservation().status()).isEqualTo(Reservation.State.EXPIRED);
        assertThatThrownBy(() -> parking.transition("one",r.id(),"confirm")).hasMessageContaining("409");
    }
    @Test void disallowsSpecialUnknownStaleAndMultipleSessionReservations() {
        assertThatThrownBy(() -> parking.reserve("one","PCD")).hasMessageContaining("409");
        parking.reserve("one","A");
        assertThatThrownBy(() -> parking.reserve("one","B")).hasMessageContaining("409");
        clock.time = clock.time.plusSeconds(46);
        assertThatThrownBy(() -> parking.reserve("two","B")).hasMessageContaining("409");
        assertThat(parking.snapshot().summary().unknown()).isEqualTo(4);
    }
    @Test void concurrentClientsOnlyOneSucceeds() throws Exception {
        var executor = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (String owner : List.of("one","two")) results.add(executor.submit(() -> {
                start.await();
                try { parking.reserve(owner,"A"); return true; }
                catch (org.springframework.web.server.ResponseStatusException e) { assertThat(e.getStatusCode().value()).isEqualTo(409); return false; }
            }));
            start.countDown();
            assertThat(List.of(results.get(0).get(10,TimeUnit.SECONDS),results.get(1).get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(reservations.findByActiveSpotIdIsNotNull()).hasSize(1);
        } finally { executor.shutdownNow(); }
    }
    @Test void sessionSurvivesReloadAndNeverAppearsInPublicSnapshot() throws Exception {
        var response = mvc.perform(get("/api/v1/reservations/current")).andExpect(status().isOk()).andReturn().getResponse();
        var cookie = response.getCookie("parking_session");
        assertThat(cookie).isNotNull(); assertThat(cookie.isHttpOnly()).isTrue();
        mvc.perform(post("/api/v1/reservations").cookie(cookie).contentType("application/json").content("{\"spotCode\":\"A\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/reservations").cookie(cookie).header("X-Parking-Client","mobile").contentType("application/json").content("{\"spotCode\":\"A\"}"))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/reservations/current").cookie(cookie)).andExpect(jsonPath("$.reservation.status").value("ACTIVE"));
        var json = mvc.perform(get("/api/v1/parking-spots")).andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain(cookie.getValue(),"tokenHash","reservation_token_hash");
        assertThat(reservations.findAll().get(0).tokenHash).isNotEqualTo(cookie.getValue()).hasSize(64);
    }
}
