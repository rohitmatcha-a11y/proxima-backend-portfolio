package com.jobportal.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health", description = "Health check endpoints")
public class HealthController {

    private final HealthEndpoint healthEndpoint;
    private final String applicationVersion;

    public HealthController(HealthEndpoint healthEndpoint,
                            @Value("${info.app.version:unknown}") String applicationVersion) {
        this.healthEndpoint = healthEndpoint;
        this.applicationVersion = applicationVersion;
    }

    @GetMapping("/health")
    @Operation(summary = "Public health check", description = "Returns an aggregate API status without infrastructure details")
    public ResponseEntity<Map<String, Object>> health() {
        HealthComponent health = healthEndpoint.health();
        boolean healthy = Status.UP.equals(health.getStatus());
        return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", healthy ? "UP" : "DOWN",
                "service", "Proxima API",
                "version", applicationVersion,
                "timestamp", Instant.now()
        ));
    }

    @GetMapping("/ping")
    @Operation(summary = "Ping", description = "Simple liveness check — returns pong with current server timestamp")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "message", "pong",
                "timestamp", Instant.now()
        ));
    }
}
