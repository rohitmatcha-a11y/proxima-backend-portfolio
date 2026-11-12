package com.jobportal.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    private final HealthEndpoint healthEndpoint = mock(HealthEndpoint.class);
    private final HealthController controller = new HealthController(healthEndpoint, "test-release");

    @Test
    void returnsOnlyAggregateStatusWhenInfrastructureIsHealthy() {
        when(healthEndpoint.health()).thenReturn(Health.up().withDetail("database", "private").build());

        var response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP")
                .containsEntry("service", "Proxima API")
                .containsEntry("version", "test-release")
                .doesNotContainKey("database");
    }

    @Test
    void returnsServiceUnavailableWhenARequiredDependencyIsDown() {
        when(healthEndpoint.health()).thenReturn(Health.down().build());

        var response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "DOWN");
    }
}
