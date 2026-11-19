package com.medicore.security;

import com.medicore.entity.UserAccount;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long accessMinutes;

    public JwtService(@Value("${medicore.security.jwt-secret}") String configuredSecret,
                      @Value("${medicore.security.access-token-minutes:15}") long accessMinutes) {
        byte[] keyBytes = decodeSecret(configuredSecret);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("MEDICORE_JWT_SECRET must provide at least 256 bits of key material.");
        }
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.accessMinutes = accessMinutes;
    }

    private byte[] decodeSecret(String secret) {
        if (secret == null || secret.isBlank()) return new byte[0];
        try {
            byte[] decoded = Base64.getDecoder().decode(secret.trim());
            if (decoded.length >= 32) return decoded;
        } catch (IllegalArgumentException ignored) {
            // Treat a sufficiently long non-Base64 value as UTF-8 secret material.
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    public String createAccessToken(UserAccount user, String familyId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("MediCore")
                .issuedAt(now)
                .expiresAt(now.plus(accessMinutes, ChronoUnit.MINUTES))
                .subject(user.id.toString())
                .claim("role", user.role.name())
                .claim("family", familyId)
                .claim("security_version", user.securityVersion)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Jwt decode(String token) {
        return decoder.decode(token);
    }
}
