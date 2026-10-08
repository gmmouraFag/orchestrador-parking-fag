package br.edu.fag.parking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class Api {
    private Api() {}
    public record Point(@DecimalMin("0") @DecimalMax("1") double x,
                        @DecimalMin("0") @DecimalMax("1") double y) {}
    public record SpotInput(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String spotCode,
                            @NotNull Status status, @NotBlank @Size(max = 32) String sector,
                            boolean accessible, @PositiveOrZero int layoutOrder,
                            @NotNull @Size(min = 3, max = 12) List<@Valid Point> polygon) {}
    public record Sync(@NotBlank @Size(max = 64) String source, @NotNull Instant observedAt,
                       @NotNull @Size(max = 2000) List<@Valid SpotInput> spots) {}
    public record Event(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String spotCode,
                        @NotNull Status status, @NotNull Instant observedAt) {}
    public record Events(@NotBlank @Size(max = 64) String source,
                         @NotEmpty @Size(max = 2000) List<@Valid Event> events) {}
    public record Log(@NotBlank @Size(max = 64) String source,
                      @NotNull @Pattern(regexp = "INFO|WARN|ERROR") String level,
                      @NotBlank @Size(max = 2000) String message, @NotNull Instant createdAt) {}
    public record Logs(@NotEmpty @Size(max = 100) List<@Valid Log> logs) {}
    public record Applied(int applied, int ignored) {}
    public record SpotView(String spotCode, Status status, Instant lastUpdated, Instant lastObserved,
                           String sector, boolean accessible, int layoutOrder, List<Point> polygon) {}
    public record Summary(int total, long free, long occupied, long unknown, Double occupancyPercent) {}
    public record Snapshot(List<SpotView> spots, Summary summary, Instant serverTime,
                           Instant lastObserved, boolean monitoringActive) {}
    public record Error(String code, String message, Instant timestamp) {}
}
