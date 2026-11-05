package br.com.losystem.collector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

public record TripHistory(
        Integer version,
        Long updatedAt,
        List<Trip> items
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Trip(
            String id,
            String clientDeliveryId,
            String status,
            String reason,
            Long startedAt,
            Long endedAt,
            BigDecimal durationSeconds,
            BigDecimal movingSeconds,
            Job job,
            Truck truck,
            Finances finances,
            Metrics metrics,
            Damage damage,
            List<TripEvent> events,
            Long createdAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Job(
            String cargoName,
            BigDecimal cargoMass,
            String originCity,
            String originCompany,
            String destinationCity,
            String destinationCompany,
            BigDecimal distanceKm,
            Long gameTimeTaken
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Truck(
            String make,
            String model,
            String licensePlate,
            String trailerModel,
            String trailer2Model
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Finances(
            BigDecimal gross,
            BigDecimal expenses,
            BigDecimal netProfit,
            BigDecimal fines,
            BigDecimal tolls,
            BigDecimal fuelCost,
            BigDecimal exchangeRate,
            List<JsonNode> expensesList
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metrics(
            BigDecimal averageSpeed,
            BigDecimal maxSpeed,
            BigDecimal fuelConsumed,
            BigDecimal averageConsumption
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Damage(
            BigDecimal truckPercent,
            BigDecimal cargoPercent
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TripEvent(
            String type,
            String title,
            String detail,
            Long createdAt
    ) {}
}
