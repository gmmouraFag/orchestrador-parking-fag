package br.edu.fag.parking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface StateRepository extends JpaRepository<ParkingState, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ParkingState s where s.id = :id")
    ParkingState lock(@Param("id") Long id);
}
