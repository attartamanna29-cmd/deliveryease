package com.deliverease.dto;

import com.deliverease.model.DeliveryType;
import com.deliverease.model.OrderStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

/** Data Transfer Objects used for communication between the client and the server. */
public class Dtos {

    public record RegisterRequest(
            @NotBlank(message = "Name is required") String name,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") String email,
            @NotBlank @Size(min = 6, message = "Password must be at least 6 characters") String password,
            String phone, String address) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record AuthResponse(String token, String name, String email, String role) {}

    public record ProfileUpdate(@NotBlank(message = "Name is required") String name, String phone, String address) {}

    public record ProfileResponse(String name, String email, String phone, String address, String role) {}

    public record PartnerRequest(
            @NotBlank(message = "Partner name is required") String name,
            String phone, String vehicleType, String email, String password) {}

    public record PartnerResponse(Long id, String name, String phone, String vehicleType,
                                  boolean available, boolean active, String email) {}

    public record OrderRequest(
            @NotBlank(message = "Pickup address is required") String pickupAddress,
            @NotBlank(message = "Drop-off address is required") String dropAddress,
            @NotBlank(message = "Package details are required") String packageDetails,
            @Positive(message = "Weight must be greater than 0") double weightKg,
            @NotNull(message = "Delivery type is required") DeliveryType deliveryType) {}

    public record StatusUpdate(@NotNull OrderStatus status, String pin) {}

    public record OrderResponse(Long id, String trackingId, String customerName, String pickupAddress,
                                String dropAddress, String packageDetails, double weightKg,
                                DeliveryType deliveryType, double charge, OrderStatus status,
                                Long partnerId, String partnerName, String deliveryPin,
                                LocalDateTime createdAt) {}

    public record HistoryItem(String status, String note, LocalDateTime time) {}

    public record TrackResponse(String trackingId, String status, String partnerName,
                                String pickupAddress, String dropAddress, List<HistoryItem> history) {}
}
