package com.documind.backend.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtDecoderTests {

    private static final String ISSUER = "https://test.supabase.invalid/auth/v1";
    private static final String AUDIENCE = "authenticated";
    private static KeyPair trustedKeyPair;
    private static KeyPair forgedKeyPair;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        trustedKeyPair = generator.generateKeyPair();
        forgedKeyPair = generator.generateKeyPair();
    }

    @Test
    void acceptsValidJwt() throws Exception {
        String subject = UUID.randomUUID().toString();

        assertThat(decoder().decode(token(trustedKeyPair, ISSUER, List.of(AUDIENCE), subject, 300)).getSubject())
            .isEqualTo(subject);
    }

    @Test
    void rejectsExpiredJwt() throws Exception {
        assertThatThrownBy(() -> decoder().decode(
            token(trustedKeyPair, ISSUER, List.of(AUDIENCE), UUID.randomUUID().toString(), -300)
        )).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongIssuer() throws Exception {
        assertThatThrownBy(() -> decoder().decode(
            token(trustedKeyPair, "https://attacker.invalid", List.of(AUDIENCE), UUID.randomUUID().toString(), 300)
        )).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        assertThatThrownBy(() -> decoder().decode(
            token(trustedKeyPair, ISSUER, List.of("another-api"), UUID.randomUUID().toString(), 300)
        )).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsForgedSignature() throws Exception {
        assertThatThrownBy(() -> decoder().decode(
            token(forgedKeyPair, ISSUER, List.of(AUDIENCE), UUID.randomUUID().toString(), 300)
        )).isInstanceOf(JwtException.class);
    }

    private NimbusJwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) trustedKeyPair.getPublic()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(ISSUER),
            new AudienceValidator(AUDIENCE)
        ));
        return decoder;
    }

    private String token(
        KeyPair keyPair,
        String issuer,
        List<String> audience,
        String subject,
        long expiresInSeconds
    ) throws Exception {
        Instant now = Instant.now();
        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),
            new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(expiresInSeconds)))
                .build()
        );
        jwt.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        return jwt.serialize();
    }
}
