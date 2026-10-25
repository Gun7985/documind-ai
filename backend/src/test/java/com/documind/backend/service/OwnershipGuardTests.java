package com.documind.backend.service;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnershipGuardTests {

    private final OwnershipGuard ownershipGuard = new OwnershipGuard();

    @Test
    void acceptsTheResourceOwner() {
        UUID userId = UUID.randomUUID();
        assertThatCode(() -> ownershipGuard.requireOwner(userId, userId)).doesNotThrowAnyException();
    }

    @Test
    void hidesAnotherUsersResourceAsNotFound() {
        assertThatThrownBy(() -> ownershipGuard.requireOwner(UUID.randomUUID(), UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404 NOT_FOUND");
    }
}
