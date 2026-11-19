package com.medicore.security;

import com.medicore.entity.UserAccount;
import com.medicore.repository.RefreshTokenRepository;
import com.medicore.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccessCookieAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;

    public AccessCookieAuthenticationFilter(JwtService jwtService, UserAccountRepository users,
                                            RefreshTokenRepository refreshTokens) {
        this.jwtService = jwtService;
        this.users = users;
        this.refreshTokens = refreshTokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String rawToken = accessToken(request);
            if (rawToken != null) authenticate(rawToken);
        }
        chain.doFilter(request, response);
    }

    private String accessToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) return authorization.substring(7);
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) if ("medicore_access".equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    private void authenticate(String token) {
        try {
            Jwt jwt = jwtService.decode(token);
            Long userId = Long.valueOf(jwt.getSubject());
            String family = jwt.getClaimAsString("family");
            String tokenRole = jwt.getClaimAsString("role");
            Integer tokenVersion = jwt.getClaim("security_version");
            if (family == null || tokenRole == null || tokenVersion == null) return;
            UserAccount user = users.findById(userId).orElse(null);
            if (user == null || !user.enabled || user.securityVersion != tokenVersion
                    || !user.role.name().equals(tokenRole)
                    || !refreshTokens.existsByFamilyIdAndUser_IdAndRevokedAtIsNullAndExpiresAtAfter(
                            family, userId, LocalDateTime.now())) return;
            MediCorePrincipal principal = new MediCorePrincipal(user.id, user.email, user.role, user.securityVersion, family);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ignored) {
            SecurityContextHolder.clearContext();
        }
    }
}
