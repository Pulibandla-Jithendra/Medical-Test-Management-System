package com.medicore.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "MEDICORE_DB_PASSWORD", matches = ".+")
class DatabaseStartupIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void startsWithMySql84FlywayAndHealthEndpoint() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();

            assertThat(metadata.getDatabaseProductName()).isEqualToIgnoringCase("MySQL");
            assertThat(metadata.getDatabaseMajorVersion()).isEqualTo(8);
            assertThat(metadata.getDatabaseMinorVersion()).isEqualTo(4);
            assertThat(connection.getCatalog()).isEqualTo("medicore_db");
        }

        // Spring runs migrate() before the application context finishes starting.
        // validate() confirms the resulting Flyway schema history is consistent.
        flyway.validate();

        ResponseEntity<String> response = restTemplate.getForEntity("/api/health", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\",\"application\":\"MediCore\"}");
    }
}
