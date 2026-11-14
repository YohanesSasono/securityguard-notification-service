package com.securityguard.notification.infrastructure;

import com.securityguard.notification.application.RecipientDirectory;
import com.securityguard.shared.http.SharedRestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** REST-backed recipient directory; identity-service remains the owner of user and membership data. */
@Component
public class IdentityRecipientDirectory implements RecipientDirectory {

    private final SharedRestClient restClient;
    private final IdentityServiceTokenSource tokenSource;
    private final String identityBaseUrl;

    public IdentityRecipientDirectory(SharedRestClient restClient, IdentityServiceTokenSource tokenSource,
                                      @Value("${services.identity.base-url}") String identityBaseUrl) {
        this.restClient = restClient;
        this.tokenSource = tokenSource;
        this.identityBaseUrl = identityBaseUrl;
    }

    @Override
    public Optional<Recipient> findRecipient(UUID companyId, UUID userId) {
        URI uri = UriComponentsBuilder.fromUriString(identityBaseUrl)
                .pathSegment("api", "v1", "internal", "companies", companyId.toString(), "recipients", userId.toString())
                .build().toUri();
        try {
            RecipientResponse response = restClient.exchange(HttpMethod.GET, uri, null, bearerHeaders(), RecipientResponse.class);
            return response == null ? Optional.empty() : Optional.of(response.toRecipient());
        } catch (HttpClientErrorException.NotFound ignored) {
            return Optional.empty();
        }
    }

    @Override
    public List<Recipient> findActiveAdmins(UUID companyId) {
        URI uri = UriComponentsBuilder.fromUriString(identityBaseUrl)
                .pathSegment("api", "v1", "internal", "companies", companyId.toString(), "admin-recipients")
                .build().toUri();
        RecipientResponse[] response = restClient.exchange(HttpMethod.GET, uri, null, bearerHeaders(), RecipientResponse[].class);
        return response == null ? List.of() : Arrays.stream(response).map(RecipientResponse::toRecipient).toList();
    }

    private HttpHeaders bearerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenSource.accessToken());
        return headers;
    }

    private record RecipientResponse(UUID userId, String email) {
        Recipient toRecipient() {
            return new Recipient(userId, email);
        }
    }
}
