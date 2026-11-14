package com.securityguard.notification.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterDeviceRequest(@NotBlank String token, @NotBlank String platform) {
}
