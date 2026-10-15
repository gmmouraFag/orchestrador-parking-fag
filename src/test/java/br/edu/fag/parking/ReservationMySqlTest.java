package br.edu.fag.parking;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@EnabledIfEnvironmentVariable(named = "MYSQL_TEST", matches = "true")
@ActiveProfiles({"test", "mysql-test"})
@Import(ReservationTest.Config.class)
@SpringBootTest(properties = "spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${TEST_DB_NAME:parking_fag_test}_reservations?createDatabaseIfNotExist=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true")
class ReservationMySqlTest extends ReservationTest {}
