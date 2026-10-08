package br.edu.fag.parking;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "system_logs")
public class SystemLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 64) String source;
    @Column(nullable = false, length = 16) String level;
    @Column(nullable = false, length = 2000) String message;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    protected SystemLog() {}
    SystemLog(String source, String level, String message, Instant createdAt) {
        this.source = source; this.level = level; this.message = message; this.createdAt = createdAt;
    }
}
