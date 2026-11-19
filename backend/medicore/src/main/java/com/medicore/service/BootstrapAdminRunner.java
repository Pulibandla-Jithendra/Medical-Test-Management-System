package com.medicore.service;

import com.medicore.entity.UserAccount;
import com.medicore.entity.UserRole;
import com.medicore.repository.UserAccountRepository;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
public class BootstrapAdminRunner implements ApplicationRunner {
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final boolean enabled;
    private final String email;
    private final String password;

    public BootstrapAdminRunner(UserAccountRepository users, PasswordEncoder passwordEncoder, AuditService audit,
                                @Value("${medicore.bootstrap.admin-enabled:false}") boolean enabled,
                                @Value("${medicore.bootstrap.admin-email:}") String email,
                                @Value("${medicore.bootstrap.admin-password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.enabled = enabled;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (users.existsByRole(UserRole.ADMIN)) {
            throw new IllegalStateException("Administrator bootstrap was enabled after an administrator already exists. Disable bootstrap configuration.");
        }
        if (email == null || email.isBlank() || password == null || password.length() < 16
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Administrator bootstrap requires an email and a password of at least 16 characters and no more than 72 UTF-8 bytes.");
        }
        String normalizedEmail = AuthService.normalizeEmail(email);
        if (users.findByEmailIgnoreCase(normalizedEmail).isPresent()) throw new IllegalStateException("Bootstrap email is already in use.");
        UserAccount admin = new UserAccount();
        admin.name = "MediCore Administrator";
        admin.email = normalizedEmail;
        admin.passwordHash = passwordEncoder.encode(password);
        admin.role = UserRole.ADMIN;
        admin.createdAt = AuthService.now();
        admin.enabled = true;
        admin.securityVersion = 0;
        users.save(admin);
        audit.record("ADMIN_BOOTSTRAPPED", admin, admin, "USER", admin.id, null, Map.of("role", "ADMIN"));
    }
}
