package com.medicore.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicore.entity.UserAccount;
import com.medicore.entity.UserRole;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtServiceTest {
    private final String validSecret = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void createsSignedRoleScopedAccessTokenWithFifteenMinuteLifetime() {
        JwtService service = new JwtService(validSecret, 15);
        UserAccount user = new UserAccount();
        user.id = 18L;
        user.role = UserRole.DOCTOR;
        user.securityVersion = 3;

        Instant before = Instant.now();
        Jwt token = service.decode(service.createAccessToken(user, "family-123"));
        Instant after = Instant.now();

        assertThat(token.getSubject()).isEqualTo("18");
        assertThat(token.getClaimAsString("role")).isEqualTo("DOCTOR");
        assertThat(token.getClaimAsString("family")).isEqualTo("family-123");
        assertThat(((Number) token.getClaim("security_version")).longValue()).isEqualTo(3L);
        long issuedAt = token.getIssuedAt().getEpochSecond();
        long expiresAt = token.getExpiresAt().getEpochSecond();
        assertThat(issuedAt).isBetween(before.getEpochSecond(), after.getEpochSecond());
        assertThat(expiresAt - issuedAt).isEqualTo(15 * 60L);
    }

    @Test
    void rejectsJwtSecretsShorterThan256Bits() {
        assertThatThrownBy(() -> new JwtService("too-short", 15))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }
}
