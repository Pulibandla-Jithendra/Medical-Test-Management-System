package com.medicore.service;

import com.medicore.dto.ApiDtos;
import com.medicore.entity.PatientProfile;
import com.medicore.entity.RefreshTokenRecord;
import com.medicore.entity.UserAccount;
import com.medicore.entity.UserRole;
import com.medicore.exception.DomainException;
import com.medicore.repository.PatientProfileRepository;
import com.medicore.repository.RefreshTokenRepository;
import com.medicore.repository.UserAccountRepository;
import com.medicore.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserAccountRepository users;
    private final PatientProfileRepository patients;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService audit;
    private final int refreshDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserAccountRepository users, PatientProfileRepository patients,
                       RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuditService audit,
                       @Value("${medicore.security.refresh-token-days:30}") int refreshDays) {
        this.users = users;
        this.patients = patients;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
        this.refreshDays = refreshDays;
    }

    @Transactional
    public UserAccount register(ApiDtos.RegisterRequest input) {
        requireBcryptSafePassword(input.password());
        String email = normalizeEmail(input.email());
        if (users.findByEmailIgnoreCase(email).isPresent()) throw DomainException.conflict("An account with that email already exists.");
        UserAccount user = new UserAccount();
        user.name = input.name().trim();
        user.email = email;
        user.passwordHash = passwordEncoder.encode(input.password());
        user.role = UserRole.PATIENT;
        user.createdAt = now();
        user.enabled = true;
        user.securityVersion = 0;
        users.save(user);

        PatientProfile profile = new PatientProfile();
        profile.user = user;
        profile.age = input.age();
        profile.gender = normalized(input.gender());
        profile.phone = normalized(input.phone());
        profile.address = normalized(input.address());
        patients.save(profile);
        audit.record("PATIENT_REGISTERED", user, user, "USER", user.id, null, java.util.Map.of("role", "PATIENT"));
        return user;
    }

    @Transactional(noRollbackFor = DomainException.class)
    public IssuedSession login(ApiDtos.LoginRequest input) {
        String email = normalizeEmail(input.email());
        UserAccount user = users.findByEmailIgnoreCase(email).orElse(null);
        boolean passwordMatches = user != null
                && input.password().getBytes(StandardCharsets.UTF_8).length <= 72
                && passwordEncoder.matches(input.password(), user.passwordHash);
        if (user == null || !user.enabled || !passwordMatches) {
            audit.record("LOGIN_FAILURE", null, user, "USER", user == null ? null : user.id, null,
                    java.util.Map.of("emailKnown", user != null));
            throw DomainException.unauthorized();
        }
        String familyId = UUID.randomUUID().toString();
        LocalDateTime issuedAt = now();
        LocalDateTime expiresAt = issuedAt.plusDays(refreshDays);
        String rawRefresh = newRefreshToken();
        RefreshTokenRecord token = newToken(user, familyId, rawRefresh, issuedAt, expiresAt);
        refreshTokens.save(token);
        audit.record("LOGIN_SUCCESS", user, user, "USER", user.id, null, java.util.Map.of("role", user.role.name()));
        return new IssuedSession(jwtService.createAccessToken(user, familyId), rawRefresh, expiresAt, user);
    }

    @Transactional(noRollbackFor = DomainException.class)
    public IssuedSession refresh(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) throw DomainException.unauthorized();
        String hash = hash(rawRefresh);
        RefreshTokenRecord current = refreshTokens.findByHashForUpdate(hash).orElse(null);
        LocalDateTime now = now();
        if (current == null) throw DomainException.unauthorized();
        if (current.usedAt != null || current.revokedAt != null) {
            refreshTokens.revokeFamily(current.familyId, now);
            audit.record("REFRESH_REUSE_DETECTED", current.user, current.user, "REFRESH_TOKEN", current.familyId,
                    null, java.util.Map.of("familyRevoked", true));
            throw DomainException.unauthorized();
        }
        if (current.expiresAt.isBefore(now) || !current.user.enabled) {
            refreshTokens.revokeFamily(current.familyId, now);
            audit.record("REFRESH_REJECTED", current.user, current.user, "REFRESH_TOKEN", current.familyId,
                    null, java.util.Map.of("reason", "expired_or_disabled"));
            throw DomainException.unauthorized();
        }
        current.usedAt = now;
        String replacementRaw = newRefreshToken();
        RefreshTokenRecord replacement = newToken(current.user, current.familyId, replacementRaw, now, current.expiresAt);
        refreshTokens.save(replacement);
        current.replacedBy = replacement;
        refreshTokens.save(current);
        audit.record("REFRESH_ROTATED", current.user, current.user, "REFRESH_TOKEN", current.familyId,
                null, java.util.Map.of("rotated", true));
        return new IssuedSession(jwtService.createAccessToken(current.user, current.familyId), replacementRaw,
                current.expiresAt, current.user);
    }

    @Transactional
    public void logout(Long userId, String familyId) {
        refreshTokens.revokeFamily(familyId, now());
        UserAccount user = users.findById(userId).orElse(null);
        audit.record("LOGOUT", user, user, "REFRESH_TOKEN", familyId, null, java.util.Map.of("revoked", true));
    }

    @Transactional
    public void logoutAll(UserAccount user) {
        LocalDateTime now = now();
        refreshTokens.revokeAllForUser(user.id, now);
        user.securityVersion++;
        users.save(user);
        audit.record("LOGOUT_ALL", user, user, "USER", user.id, null,
                java.util.Map.of("securityVersion", user.securityVersion));
    }

    public String createRefreshTokenValue() { return newRefreshToken(); }

    public static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    public static String normalizeEmail(String email) { return email.trim().toLowerCase(java.util.Locale.ROOT); }
    public static String normalized(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    static void requireBcryptSafePassword(String password) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw DomainException.invalid("Passwords must be no more than 72 UTF-8 bytes when using BCrypt.");
        }
    }

    private RefreshTokenRecord newToken(UserAccount user, String familyId, String raw, LocalDateTime issuedAt,
                                        LocalDateTime expiresAt) {
        RefreshTokenRecord token = new RefreshTokenRecord();
        token.id = UUID.randomUUID().toString();
        token.user = user;
        token.familyId = familyId;
        token.tokenHash = hash(raw);
        token.issuedAt = issuedAt;
        token.expiresAt = expiresAt;
        return token;
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.");
        }
    }

    public record IssuedSession(String accessToken, String refreshToken, LocalDateTime expiresAt, UserAccount user) {}
}
