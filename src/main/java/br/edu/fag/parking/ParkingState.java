package br.edu.fag.parking;

import jakarta.persistence.*;

@Entity @Table(name = "parking_state")
public class ParkingState {
    @Id Long id = 1L;
    @Column(name = "state_version", nullable = false) long version;
    @Column(name = "monitoring_active", nullable = false) boolean monitoringActive;
}
