package com.changnian.auth;

public record CurrentUser(Long id, Long familyId, Role role, String username, String displayName) {
}
