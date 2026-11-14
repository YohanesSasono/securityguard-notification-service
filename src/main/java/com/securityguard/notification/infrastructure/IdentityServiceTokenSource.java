package com.securityguard.notification.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;

/** Obtains a short-lived service token for asynchronous identity lookups. */
@Component
final class IdentityServiceTokenSource {

    private static final long REFRESH_SKEW_SECONDS = 30;

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String scopes;
    private final Clock clock = Clock.systemUTC();

    private String cachedToken;
    private Instant cachedUntil = Instant.EPOCH;

    IdentityServiceTokenSource(
            RestClient.Builder builder,
            @Value("${services.identity.base-url}") String identityBaseUrl,
            @Value("${services.identity.client-id:securityguard-notification}") String clientId,
            @Value("${services.identity.client-secret:}") String clientSecret,
            @Value("${services.identity.scopes:users.read}") String scopes) {
        this.restClient = builder.clone().baseUrl(identityBaseUrl).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scopes = scopes;
    }

    synchronized String accessToken() {
        Instant now = clock.instant();
        if (cachedToken != null && now.isBefore(cachedUntil)) {
            return cachedToken;
        }
        if (!StringUtils.hasText(clientSecret)) {
            throw new IllegalStateException("services.identity.client-secret is not configured");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", scopes);

        TokenResponse response = restClient.post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || !StringUtils.hasText(response.accessToken())) {
            throw new IllegalStateException("identity-service returned no access token");
        }

        cachedToken = response.accessToken();
        cachedUntil = now.plusSeconds(Math.max(0, response.expiresIn() - REFRESH_SKEW_SECONDS));
        return cachedToken;
    }

    private record TokenResponse(@JsonProperty("access_token") String accessToken,
                                 @JsonProperty("expires_in") long expiresIn) {
    }
}
