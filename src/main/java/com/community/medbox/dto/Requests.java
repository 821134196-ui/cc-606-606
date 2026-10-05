package com.community.medbox.dto;

import jakarta.validation.constraints.*;

public record Requests() {

    public record CheckRequest(@NotNull @Min(0) Integer receivedQty) {}

    public record ConfirmDifferenceRequest(@NotBlank String queryCode, String note) {}

    public record DrugRequest(
            @NotBlank String name,
            @NotBlank String category,
            @Min(1) int quantity,
            Boolean safetyConfirmed) {}

    public record SealRequest(
            @NotBlank String category,
            @NotBlank String storageLocation,
            Double fridgeTemp) {}

    public record ReopenRequest(
            @NotBlank String operator,
            @NotBlank String reviewer,
            @NotBlank String reason) {}

    public record TransferRequest(
            @NotBlank String fromUser,
            @NotBlank String toUser,
            @NotBlank String location,
            Double coldTemp,
            String scanCode) {}

    public record HandoverRequest(
            @NotBlank String fromUser,
            @NotBlank String disposalUser,
            @NotBlank String scanCode,
            Double coldTemp,
            String location) {}

    public record SignRequest(@NotBlank String disposalUser, @NotBlank String scanCode) {}

    public record ColdLogRequest(
            @NotBlank String action,
            @NotNull Double temperature,
            String fridgeNo,
            @NotBlank String operator,
            String note) {}

    public record ReceiptQuery(@NotBlank String apptNo, @NotBlank String queryCode) {}

    public record AppointmentRequest(
            @NotBlank String residentName,
            @NotBlank String phone,
            String queryCode,
            @Min(1) int declaredQty) {}
}
