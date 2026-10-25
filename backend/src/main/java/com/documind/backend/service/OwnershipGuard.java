package com.documind.backend.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OwnershipGuard {

    public void requireOwner(UUID authenticatedUserId, UUID resourceOwnerId) {
        if (!authenticatedUserId.equals(resourceOwnerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
        }
    }
}
