package com.securityguard.notification.presentation.dto;

import com.securityguard.notification.domain.NotificationPreference;

public record PreferencesResponse(boolean fcmEnabled, boolean emailEnabled) {
    public static PreferencesResponse from(NotificationPreference preference) {
        return new PreferencesResponse(preference.isFcmEnabled(), preference.isEmailEnabled());
    }
}
