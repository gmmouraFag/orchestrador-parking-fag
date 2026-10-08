package br.edu.fag.parking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SpotRepository extends JpaRepository<Spot, Long> {
    Optional<Spot> findBySpotCode(String spotCode);
    List<Spot> findAllByOrderBySectorAscLayoutOrderAsc();
}
