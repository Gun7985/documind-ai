package com.documind.backend.config;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserTests {

    @Test
    void extractsUuidFromVerifiedSubject() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();

        assertThat(new CurrentUser().requireUserId(new JwtAuthenticationToken(jwt))).isEqualTo(userId);
    }
}
