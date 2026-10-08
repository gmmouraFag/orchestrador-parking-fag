package br.edu.fag.parking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class LogService {
    private static final Logger logger = LoggerFactory.getLogger(LogService.class);
    private final LogRepository logs;
    private final long retention;
    public LogService(LogRepository logs, @Value("${parking.log-retention-days}") long retention) {
        this.logs = logs; this.retention = Math.max(1, retention);
    }
    @Transactional
    public Api.Applied receive(Api.Logs request) {
        int applied = 0;
        for (var log : request.logs()) {
            var created = log.createdAt().truncatedTo(ChronoUnit.MICROS);
            if (log.createdAt().isAfter(Instant.now().plusSeconds(30)))
                throw new IllegalArgumentException("Data do log excessivamente no futuro");
            if (!logs.existsBySourceAndLevelAndMessageAndCreatedAt(log.source(), log.level(), log.message(), created)) {
                logs.save(new SystemLog(log.source(), log.level(), log.message(), created)); applied++;
            }
        }
        return new Api.Applied(applied, request.logs().size() - applied);
    }
    @Transactional
    public void internal(String level, String message) {
        logs.save(new SystemLog("java", level, message, Instant.now()));
        switch (level) {
            case "ERROR" -> logger.error("operational_event message={}", message);
            case "WARN" -> logger.warn("operational_event message={}", message);
            default -> logger.info("operational_event message={}", message);
        }
    }
    @Scheduled(fixedDelayString = "${LOG_CLEANUP_MS:3600000}")
    @Transactional
    public void cleanup() {
        long count = logs.deleteByCreatedAtBefore(Instant.now().minus(retention, ChronoUnit.DAYS));
        if (count > 0) logger.info("log_cleanup deleted={}", count);
    }
}
