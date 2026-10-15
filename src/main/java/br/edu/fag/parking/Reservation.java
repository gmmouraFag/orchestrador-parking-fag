package br.edu.fag.parking;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "parking_reservations")
public class Reservation {
    public enum State { ACTIVE, PENDING_CONFIRMATION, EXPIRED, COMPLETED, CANCELLED, DISPLACED }
    @Id @Column(length = 36) String id;
    @Column(name = "spot_id", nullable = false) Long spotId;
    @Column(name = "spot_code", nullable = false, length = 64) String spotCode;
    @Column(name = "reservation_token_hash", nullable = false, length = 64) String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) State status;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "expires_at", nullable = false) Instant expiresAt;
    @Column(name = "confirmed_at") Instant confirmedAt;
    @Column(name = "active_spot_id", unique = true) Long activeSpotId;
    @Column(name = "active_session_hash", unique = true, length = 64) String activeSessionHash;
    boolean live() { return status == State.ACTIVE || status == State.PENDING_CONFIRMATION; }
    void end(State next) { status = next; activeSpotId = null; activeSessionHash = null; }
}
