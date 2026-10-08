package br.edu.fag.parking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;

public interface LogRepository extends JpaRepository<SystemLog, Long> {
    long deleteByCreatedAtBefore(Instant cutoff);
    boolean existsBySourceAndLevelAndMessageAndCreatedAt(String source, String level, String message, Instant createdAt);
}
