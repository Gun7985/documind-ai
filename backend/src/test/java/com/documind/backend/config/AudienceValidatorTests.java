package com.documind.backend.config;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class AudienceValidatorTests {

    private final AudienceValidator validator = new AudienceValidator("authenticated");

    @Test
    void acceptsRequiredAudience() {
        assertThat(validator.validate(jwt(List.of("authenticated"))).hasErrors()).isFalse();
    }

    @Test
    void rejectsWrongAudience() {
        assertThat(validator.validate(jwt(List.of("another-api"))).hasErrors()).isTrue();
    }

    private Jwt jwt(List<String> audience) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("7c7638b4-b37e-4d77-9b26-b36ad27751dc")
            .audience(audience)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
    }
}
