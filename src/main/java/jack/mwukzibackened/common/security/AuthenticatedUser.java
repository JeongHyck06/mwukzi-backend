package jack.mwukzibackened.common.security;

import lombok.Getter;

import java.util.UUID;

@Getter
public class AuthenticatedUser {
    private final UUID userId;
    private final String provider;
    private final String role;

    public AuthenticatedUser(UUID userId, String provider, String role) {
        this.userId = userId;
        this.provider = provider;
        this.role = role;
    }
}
