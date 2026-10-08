CREATE TABLE parking_spots (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    spot_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    last_updated TIMESTAMP(6) NOT NULL,
    last_observed TIMESTAMP(6) NOT NULL,
    sector VARCHAR(32) NOT NULL,
    is_accessible BOOLEAN NOT NULL DEFAULT FALSE,
    layout_order INT NOT NULL,
    polygon_json TEXT NOT NULL,
    UNIQUE KEY uk_spot_code (spot_code),
    INDEX ix_last_observed (last_observed),
    CONSTRAINT chk_spot_status CHECK (status IN ('FREE', 'OCCUPIED', 'UNKNOWN'))
);
CREATE TABLE system_logs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source VARCHAR(64) NOT NULL,
    level VARCHAR(16) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    INDEX ix_log_created (created_at)
);
