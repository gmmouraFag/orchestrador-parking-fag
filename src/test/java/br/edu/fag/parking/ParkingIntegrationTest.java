package br.edu.fag.parking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ParkingIntegrationTest {
    @Autowired ParkingService service;
    @Autowired SpotRepository spots;
    @Autowired LogRepository logs;
    @Autowired LogService logService;
    @Autowired MockMvc mvc;
    final List<Api.Point> polygon = List.of(new Api.Point(.1,.1), new Api.Point(.2,.1), new Api.Point(.2,.2));
    @BeforeEach void clear() { spots.deleteAll(); logs.deleteAll(); }
    Api.Sync sync(Instant time, Status status) {
        return new Api.Sync("parking-video", time, List.of(new Api.SpotInput("A-01", status, "A", true, 0, polygon)));
    }
    @Test void stateTransitionsAreIdempotentAndRejectOlderObservations() {
        var first = Instant.now().minusSeconds(20).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        assertThat(service.sync(sync(first, Status.FREE)).applied()).isEqualTo(1);
        assertThat(service.sync(sync(first, Status.FREE)).ignored()).isEqualTo(1);
        var later = first.plusSeconds(5);
        service.events(new Api.Events("parking-video", List.of(new Api.Event("A-01", Status.OCCUPIED, later))));
        service.events(new Api.Events("parking-video", List.of(new Api.Event("A-01", Status.FREE, first))));
        var spot = service.snapshot().spots().get(0);
        assertThat(spot.status()).isEqualTo(Status.OCCUPIED);
        assertThat(spot.lastUpdated()).isEqualTo(later);
        service.sync(sync(later.plusSeconds(5), Status.OCCUPIED));
        assertThat(service.snapshot().spots().get(0).lastUpdated()).isEqualTo(later);
        assertThat(service.snapshot().summary().occupancyPercent()).isEqualTo(100.0);
    }
    @Test void staleMonitoringRetainsStateAndUnknownHasNoOccupancy() {
        service.sync(sync(Instant.now().minusSeconds(100), Status.FREE));
        assertThat(service.snapshot().monitoringActive()).isFalse();
        assertThat(service.snapshot().spots().get(0).status()).isEqualTo(Status.FREE);
        service.sync(sync(Instant.now(), Status.UNKNOWN));
        assertThat(service.snapshot().summary().occupancyPercent()).isNull();
        assertThat(service.snapshot().summary().unknown()).isEqualTo(1);
    }
    @Test void emptyNewSnapshotRemovesSpotsAndOldSnapshotDoesNotRollBack() {
        var now = Instant.now().minusSeconds(2);
        service.sync(sync(now, Status.FREE));
        service.sync(new Api.Sync("parking-video", now.minusSeconds(1), List.of()));
        assertThat(spots.count()).isEqualTo(1);
        service.sync(new Api.Sync("parking-video", now.plusSeconds(1), List.of()));
        assertThat(spots.count()).isZero();
    }
    @Test void httpContractsValidateAndRequireConfiguredKey() throws Exception {
        mvc.perform(post("/api/v1/parking-spots/sync").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/parking-spots/sync").header("X-API-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        service.sync(sync(Instant.now(), Status.FREE));
        mvc.perform(get("/api/v1/parking-spots")).andExpect(status().isOk()).andExpect(jsonPath("$.summary.free").value(1)).andExpect(jsonPath("$.spots[0].accessible").value(true));
        mvc.perform(get("/api/v1/parking-spots/summary")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
    @Test void operationalLogsDeduplicateAndExpire() {
        var log = new Api.Log("python", "WARN", "Captura interrompida", Instant.now().minusSeconds(10));
        var request = new Api.Logs(List.of(log));
        assertThat(logService.receive(request).applied()).isEqualTo(1);
        assertThat(logService.receive(request).ignored()).isEqualTo(1);
        logs.save(new SystemLog("python", "INFO", "Antigo", Instant.now().minusSeconds(86400 * 10)));
        logService.cleanup();
        assertThat(logs.count()).isEqualTo(1);
    }
    @Test void rejectsInvalidCatalogAndUnknownSpotEventsAtomically() {
        var first = Instant.now().minusSeconds(5);
        service.sync(sync(first, Status.FREE));
        var mixed = new Api.Events("parking-video", List.of(new Api.Event("A-01", Status.OCCUPIED, first.plusSeconds(1)),
                new Api.Event("NO-SUCH-SPOT", Status.OCCUPIED, first.plusSeconds(1))));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.events(mixed)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.snapshot().spots().get(0).status()).isEqualTo(Status.FREE);
        var duplicate = new Api.Sync("parking-video", Instant.now(), List.of(sync(first, Status.FREE).spots().get(0), sync(first, Status.FREE).spots().get(0)));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sync(duplicate)).isInstanceOf(IllegalArgumentException.class);
        var future = sync(Instant.now().plusSeconds(60), Status.OCCUPIED);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sync(future)).isInstanceOf(IllegalArgumentException.class);
        var emptyPolygon = List.of(new Api.Point(0,0), new Api.Point(0,0), new Api.Point(0,0));
        var invalid = new Api.Sync("parking-video", Instant.now(), List.of(new Api.SpotInput("A-02", Status.FREE, "A", false, 1, emptyPolygon)));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sync(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThat(spots.count()).isEqualTo(1);
    }
}
