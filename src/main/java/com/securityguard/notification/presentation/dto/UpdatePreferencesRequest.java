package com.securityguard.notification.presentation.dto;

public record UpdatePreferencesRequest(Boolean fcmEnabled, Boolean emailEnabled) {
}
