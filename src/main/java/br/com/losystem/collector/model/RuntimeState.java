package br.com.losystem.collector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeState(TachographState tachograph_state) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TachographState(
            Integer version,
            Shift activeShift,
            List<Shift> history
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Shift(
            String id,
            Long startedAt,
            Long endedAt,
            String simulator,
            Truck truck,
            BigDecimal startOdometerKm,
            BigDecimal lastOdometerKm,
            BigDecimal distanceKm,
            BigDecimal maxSpeedKph,
            JsonNode declaredActivity,
            JsonNode currentSegment,
            List<Segment> segments,
            List<JsonNode> corrections,
            List<Warning> warnings,
            JsonNode drivingControl,
            JsonNode telemetry,
            Long lastRecordedAt,
            BigDecimal endOdometerKm
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Truck(
            String make,
            String model,
            String licensePlate
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Segment(
            String id,
            String category,
            String subtype,
            String source,
            Long startAt,
            Long endAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Warning(
            String id,
            String kind,
            Long at,
            Long startAt,
            Long lastTelemetryAt,
            Long detectedAt,
            Long since
    ) {}

}
