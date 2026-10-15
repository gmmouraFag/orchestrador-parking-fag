CREATE TABLE parking_state (
    id BIGINT NOT NULL PRIMARY KEY,
    state_version BIGINT NOT NULL DEFAULT 0,
    monitoring_active BOOLEAN NOT NULL DEFAULT FALSE
);
INSERT INTO parking_state (id, state_version, monitoring_active) VALUES (1, 0, FALSE);

CREATE TABLE parking_reservations (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    spot_id BIGINT NOT NULL,
    spot_code VARCHAR(64) NOT NULL,
    reservation_token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    confirmed_at TIMESTAMP(6) NULL,
    active_spot_id BIGINT NULL,
    active_session_hash VARCHAR(64) NULL,
    UNIQUE KEY uk_active_spot (active_spot_id),
    UNIQUE KEY uk_active_session (active_session_hash),
    INDEX ix_reservation_owner (reservation_token_hash, created_at),
    INDEX ix_reservation_expiry (expires_at),
    CONSTRAINT fk_reservation_spot FOREIGN KEY (spot_id) REFERENCES parking_spots(id),
    CONSTRAINT chk_reservation_state CHECK (status IN ('ACTIVE','PENDING_CONFIRMATION','EXPIRED','COMPLETED','CANCELLED','DISPLACED')),
    CONSTRAINT chk_reservation_active CHECK (
      (status IN ('ACTIVE','PENDING_CONFIRMATION') AND active_spot_id = spot_id AND active_session_hash = reservation_token_hash)
      OR (status NOT IN ('ACTIVE','PENDING_CONFIRMATION') AND active_spot_id IS NULL AND active_session_hash IS NULL)
    )
);
