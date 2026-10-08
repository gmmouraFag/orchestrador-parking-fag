package br.edu.fag.parking;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "parking_spots")
@org.hibernate.annotations.DynamicUpdate
public class Spot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "spot_code", nullable = false, unique = true, length = 64) String spotCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) Status status;
    @Column(name = "last_updated", nullable = false) Instant lastUpdated;
    @Column(name = "last_observed", nullable = false) Instant lastObserved;
    @Column(nullable = false, length = 32) String sector;
    @Column(name = "is_accessible", nullable = false) boolean accessible;
    @Column(name = "layout_order", nullable = false) int layoutOrder;
    @Column(name = "polygon_json", nullable = false, columnDefinition = "TEXT") String polygonJson;
    protected Spot() {}
}
