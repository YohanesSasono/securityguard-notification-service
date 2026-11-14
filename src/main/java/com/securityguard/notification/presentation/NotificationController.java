package com.securityguard.notification.presentation;

import com.securityguard.notification.application.NotificationService;
import com.securityguard.notification.domain.DevicePlatform;
import com.securityguard.notification.presentation.dto.*;
import com.securityguard.shared.exception.BusinessValidationException;
import com.securityguard.security.AuthenticatedUser;
import com.securityguard.security.CurrentUserProvider;
import com.securityguard.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API_SPEC_V2.txt "Notifications" section - exactly the three listed
 * endpoints. Device deregistration and a GET for preferences were
 * considered but not added: not listed in the spec, and PATCH's response
 * already returns the current preference state, covering the practical
 * need without extra surface area.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;

    public NotificationController(NotificationService notificationService, CurrentUserProvider currentUserProvider) {
        this.notificationService = notificationService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/devices")
    public ResponseEntity<DeviceResponse> registerDevice(@Valid @RequestBody RegisterDeviceRequest request) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        var command = new NotificationService.RegisterDeviceCommand(request.token(), parsePlatform(request.platform()));
        var device = notificationService.registerDevice(current.companyId(), current.userId(), command);
        return ResponseEntity.status(HttpStatus.CREATED).body(DeviceResponse.from(device));
    }

    @GetMapping
    public ResponseEntity<PageResponse<NotificationResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        var page = notificationService.listHistory(current.companyId(), current.userId(), pageable)
                .map(NotificationResponse::from);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @PatchMapping("/preferences")
    public ResponseEntity<PreferencesResponse> updatePreferences(@RequestBody UpdatePreferencesRequest request) {
        AuthenticatedUser current = currentUserProvider.getCurrentUser();
        var command = new NotificationService.UpdatePreferencesCommand(request.fcmEnabled(), request.emailEnabled());
        var preference = notificationService.updatePreferences(current.companyId(), current.userId(), command);
        return ResponseEntity.ok(PreferencesResponse.from(preference));
    }

    private DevicePlatform parsePlatform(String raw) {
        try {
            return DevicePlatform.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessValidationException("Unknown device platform: " + raw);
        }
    }
}
