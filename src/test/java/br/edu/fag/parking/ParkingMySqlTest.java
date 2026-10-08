package br.edu.fag.parking;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.ActiveProfiles;

/** Runs the same behavioral contracts against an isolated real MySQL database. */
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST", matches = "true")
@ActiveProfiles({"test", "mysql-test"})
class ParkingMySqlTest extends ParkingIntegrationTest {}
