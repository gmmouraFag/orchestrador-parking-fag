package br.edu.fag.parking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, String> {
    List<Reservation> findByActiveSpotIdIsNotNull();
    List<Reservation> findByTokenHashOrderByCreatedAtDesc(String hash);
}
