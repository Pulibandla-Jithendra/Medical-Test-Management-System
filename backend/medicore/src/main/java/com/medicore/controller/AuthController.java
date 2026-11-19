package com.medicore.controller;

import com.medicore.dto.ApiDtos;
import com.medicore.entity.UserAccount;
import com.medicore.security.MediCorePrincipal;
import com.medicore.service.AccountService;
import com.medicore.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final AccountService accounts;
    private final boolean secureCookies;

    public AuthController(AuthService authService, AccountService accounts,
                          @Value("${medicore.security.cookie-secure:false}") boolean secureCookies) {
        this.authService = authService;
        this.accounts = accounts;
        this.secureCookies = secureCookies;
    }

    @GetMapping("/csrf")
    public CsrfView csrf(CsrfToken token) { return new CsrfView(token.getToken()); }

    @PostMapping("/register")
    public ResponseEntity<ApiDtos.UserView> register(@Valid @RequestBody ApiDtos.RegisterRequest request) {
        UserAccount user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(accounts.currentUser(user.id));
    }

    @PostMapping("/login")
    public ApiDtos.UserView login(@Valid @RequestBody ApiDtos.LoginRequest request, HttpServletResponse response) {
        AuthService.IssuedSession session = authService.login(request);
        writeCookies(response, session);
        return accounts.currentUser(session.user().id);
    }

    @PostMapping("/refresh")
    public ApiDtos.UserView refresh(HttpServletRequest request, HttpServletResponse response) {
        AuthService.IssuedSession session = authService.refresh(cookie(request, "medicore_refresh"));
        writeCookies(response, session);
        return accounts.currentUser(session.user().id);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal MediCorePrincipal principal, HttpServletResponse response) {
        authService.logout(principal.userId(), principal.familyId());
        clearCookies(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal MediCorePrincipal principal, HttpServletResponse response) {
        authService.logoutAll(accounts.requireUser(principal.userId()));
        clearCookies(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ApiDtos.UserView me(@AuthenticationPrincipal MediCorePrincipal principal) {
        return accounts.currentUser(principal.userId());
    }

    private void writeCookies(HttpServletResponse response, AuthService.IssuedSession session) {
        ResponseCookie access = ResponseCookie.from("medicore_access", session.accessToken())
                .httpOnly(true).secure(secureCookies).sameSite("Lax").path("/api/v1")
                .maxAge(Duration.ofMinutes(15)).build();
        long seconds = Math.max(0, Duration.between(LocalDateTime.now(ZoneOffset.UTC), session.expiresAt()).getSeconds());
        ResponseCookie refresh = ResponseCookie.from("medicore_refresh", session.refreshToken())
                .httpOnly(true).secure(secureCookies).sameSite("Lax").path("/api/v1/auth")
                .maxAge(Duration.ofSeconds(seconds)).build();
        response.addHeader(HttpHeaders.SET_COOKIE, access.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refresh.toString());
    }

    private void clearCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("medicore_access", "")
                .httpOnly(true).secure(secureCookies).sameSite("Lax").path("/api/v1").maxAge(0).build().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("medicore_refresh", "")
                .httpOnly(true).secure(secureCookies).sameSite("Lax").path("/api/v1/auth").maxAge(0).build().toString());
    }

    private String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (jakarta.servlet.http.Cookie item : request.getCookies()) if (name.equals(item.getName())) return item.getValue();
        return null;
    }

    public record CsrfView(String token) {}
}
